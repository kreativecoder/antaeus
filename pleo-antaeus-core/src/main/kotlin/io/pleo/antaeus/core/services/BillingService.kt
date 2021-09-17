package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal,
    private val invoiceService: InvoiceService
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Get all pending invoices from the database
     * and update their statuses based on response from provider.
     *
     */
    fun chargePendingInvoices() {
        invoiceService.fetchPendingInvoices()
            .forEach { invoice -> chargeInvoice(invoice) }
    }

    /**
     * Charge a single invoice, update the invoice status
     * based on the response from the payment provider.
     *
     * @param invoice - Invoice to be charged.
     */
    fun chargeInvoice(invoice: Invoice) {
        logger.info { "processing invoice ${invoice.id}" }

        val charged: Boolean
        try {
            charged = paymentProvider.charge(invoice)

            if (charged) {
                logger.info { "Invoice: ${invoice.id} payment was successful." }
                dal.updateInvoiceStatus(invoice.id, InvoiceStatus.PAID)
            } else {
                logger.info { "Payment was declined by payment provider: ${invoice.id}" }
                dal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
            }

        } catch (ex: Exception) {
            logger.error(ex) { "Error processing invoice..." }
            handleException(ex)
        }
    }

    /**
     * Exception handling logic goes here
     */
    private fun handleException(exception: Exception) {
        //todo handle known exceptions based on business logic.
    }
}
