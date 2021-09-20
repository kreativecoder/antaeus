package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.external.CurrencyConverter
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BillingServiceTest {
    private val paymentProvider = mockk<PaymentProvider> {}
    private val invoiceService = mockk<InvoiceService> {
        every { updateStatus(any(), any()) } returns Unit
    }
    private val customerService = mockk<CustomerService> {}
    private val currencyConverter = mockk<CurrencyConverter>()

    private val billingService = BillingService(
        paymentProvider = paymentProvider,
        invoiceService = invoiceService,
        customerService = customerService,
        currencyConverter = currencyConverter
    )

    @Test
    fun `test charge all pending invoices`() {
        //Arrange
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)
        val pendingInvoice2 = createInvoice(InvoiceStatus.PENDING)
        every { invoiceService.fetchPending() } returns listOf(pendingInvoice, pendingInvoice2)
        every { paymentProvider.charge(any()) } returns true

        //Act
        billingService.chargePendingInvoices()

        //Assert
        verify(exactly = 2) {
            invoiceService.updateStatus(
                id = or(pendingInvoice.id, pendingInvoice2.id),
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
            invoiceService.updateStatus(
                id = pendingInvoice.id,
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
            invoiceService.updateStatus(
                id = pendingInvoice.id,
                status = InvoiceStatus.FAILED
            )
        }
    }

    @Test
    fun `test charge is retried with correct amount & currency`() {
        //Arrange
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)
        val customer = Customer(pendingInvoice.customerId, currency = Currency.GBP)
        every { paymentProvider.charge(any()) } throws
                CurrencyMismatchException(pendingInvoice.id, pendingInvoice.customerId) andThen true
        every { customerService.fetch(any()) } returns customer
        every { currencyConverter.convert(any(), any(), any()) } returns BigDecimal(20)

        //Act
        billingService.chargeInvoice(pendingInvoice)

        //Assert
        verify {
            customerService.fetch(id = pendingInvoice.customerId)
            currencyConverter.convert(
                amount = pendingInvoice.amount.value,
                fromCurrency = pendingInvoice.amount.currency,
                toCurrency = customer.currency
            )

            invoiceService.updateStatus(
                id = pendingInvoice.id,
                status = InvoiceStatus.PAID
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
