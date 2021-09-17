package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BillingServiceTest {
    private val paymentProvider = mockk<PaymentProvider> {}
    private val dal = mockk<AntaeusDal> {
        every { updateInvoiceStatus(any(), any()) } returns Unit
    }
    private val invoiceService = mockk<InvoiceService> {}

    private val billingService = BillingService(
        paymentProvider = paymentProvider,
        dal = dal,
        invoiceService = invoiceService
    )

    @Test
    fun `test charge all pending invoices`() {
        //Arrange
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)
        val pendingInvoice2 = createInvoice(InvoiceStatus.PENDING)
        every { invoiceService.fetchPendingInvoices() } returns listOf(pendingInvoice, pendingInvoice2)
        every { paymentProvider.charge(any()) } returns true

        //Act
        billingService.chargePendingInvoices()

        //Assert
        verify(exactly = 2) {
            dal.updateInvoiceStatus(
                invoiceId = or(pendingInvoice.id, pendingInvoice2.id),
                status = InvoiceStatus.PAID
            )
        }
    }

    @Test
    fun `test invoice is updated to paid when charge is successful`() {
        //Arrange
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)
        every { paymentProvider.charge(pendingInvoice) } returns true

        //Act
        billingService.chargeInvoice(pendingInvoice)

        //Assert
        verify {
            dal.updateInvoiceStatus(
                invoiceId = pendingInvoice.id,
                status = InvoiceStatus.PAID
            )
        }
    }

    @Test
    fun `test invoice is updated to failed when charge failed`() {
        //Arrange
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)
        every { paymentProvider.charge(pendingInvoice) } returns false

        //Act
        billingService.chargeInvoice(pendingInvoice)

        //Assert
        verify {
            dal.updateInvoiceStatus(
                invoiceId = pendingInvoice.id,
                status = InvoiceStatus.FAILED
            )
        }
    }

    private fun createInvoice(status: InvoiceStatus): Invoice {
        return Invoice(
            id = 10,
            customerId = 20,
            status = status,
            amount = Money(
                value =
                BigDecimal(100),
                currency = Currency.EUR
            )
        )
    }
}
