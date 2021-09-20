/*
    This is the payment provider. It is a "mock" of an external service that you can pretend runs on another system.
    With this API you can ask customers to pay an invoice.

    This mock will succeed if the customer has enough money in their balance,
    however the documentation lays out scenarios in which paying an invoice could fail.
 */

package io.pleo.antaeus.core.external

import io.pleo.antaeus.models.Currency
import java.math.BigDecimal

interface CurrencyConverter {
    /*
        Convert's from one currency to another.

        Returns:
          amount in the `to` currency.
     */

    fun convert(amount: BigDecimal, fromCurrency: Currency, toCurrency: Currency): BigDecimal
}
