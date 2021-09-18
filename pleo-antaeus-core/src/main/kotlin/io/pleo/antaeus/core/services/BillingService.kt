package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Get all pending invoices from the database
     * and update their statuses based on response from provider.
     *
     */
    fun chargePendingInvoices() {
        invoiceService.fetchPending()
            .forEach { invoice -> chargeInvoice(invoice) }
    }

    /**
     * Charge a single invoice, update the invoice status
     * based on the response from the payment provider.
     *
     * @param invoice - Invoice to be charged.
     */
    fun chargeInvoice(invoice: Invoice) {
        logger.info { "started processing invoice(${invoice.id})" }

        val charged: Boolean
        try {
            charged = paymentProvider.charge(invoice)

            if (charged) {
                logger.info { "invoice(${invoice.id}) payment successful." }
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID)
            } else {
                logger.info { "invoice(${invoice.id}) payment failed." }
                invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED)
            }

        } catch (ex: Exception) {
            logger.error(ex) { "invoice(${invoice.id}) payment error." }
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
