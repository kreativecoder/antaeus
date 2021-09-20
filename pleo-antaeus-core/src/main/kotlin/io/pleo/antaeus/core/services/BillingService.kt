package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.lang.Thread.sleep

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {
    private val logger = KotlinLogging.logger { }
    private val maxRetries = System.getenv().getOrDefault("PAYMENT_MAX_RETRIES", 3.toString());

    /**
     * Get all pending invoices from the database
     * and update their statuses based on response from provider.
     *
     */
    fun chargePendingInvoices() {
        invoiceService.fetchPending()
            .filter { it.status == InvoiceStatus.PENDING }
            .forEach { invoice -> chargeInvoice(invoice) }
    }

    /**
     * Charge a single invoice, update the invoice status
     * based on the response from the payment provider.
     *
     * @param invoice - Invoice to be charged.
     */
    fun chargeInvoice(invoice: Invoice, retryCount: Int = 0) {
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
            handleException(ex, invoice, retryCount)
        }
    }

    /**
     * Exception handling logic goes here
     */
    private fun handleException(exception: Exception, invoice: Invoice, retryCount: Int) {
        when (exception) {
            is CustomerNotFoundException -> {
                //this error should be reported first, an action can be performed based on business logic
                // e.g. create customer, notify customer etc
                logger.error { "customer(${invoice.customerId}) not found on payment provider." }
            }
            is CurrencyMismatchException -> {
                // we can minimize the rate of this error by confirming that the currency on invoice
                // is same as customers currency, if this still occurs, then a business rule determines
                // what is done next.
                logger.error { "customer(${invoice.customerId}) invoice currency does not match on payment provider." }
            }
            is NetworkException -> {
                // we'll retry this right away, on a prod environment however,
                // the mechanism for handling this will be different
                if (retryCount < maxRetries.toInt()) {
                    logger.info { "retrying invoice(${invoice.id}) due to network error." }
                    sleep(60000)
                    chargeInvoice(invoice, retryCount + 1)
                } else {
                    logger.error { "could not retry invoice(${invoice.id}), max retries reached." }
                }

            }
            else -> {
                //this is fatal, an error we don't understand...proper logging and alerting will work here
                logger.error { "unknown error occurred charging invoice(${invoice.id})" }
            }
        }
    }
}
