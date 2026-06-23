package com.simpletickr.transaction

import com.simpletickr.fx.FxRateSource
import java.math.BigDecimal
import java.time.LocalDate

enum class TransactionType { BUY, SELL }

data class Transaction(
    val id: Long,
    val portfolioId: Long,
    val listingId: Long,
    val assetId: Long,
    val type: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
    // FX rate at execution time: 1 baseCurrency = fxRate listingCurrency.
    // Null when listing currency == base currency, or for transactions recorded before FX tracking was added.
    val fxRate: BigDecimal? = null,
    val fxRateSource: FxRateSource? = null,
    val externalId: String? = null,
) {
    init {
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
        require(price >= BigDecimal.ZERO) { "Price must not be negative" }
        fees?.let { require(it >= BigDecimal.ZERO) { "Fees must not be negative" } }
        fxRate?.let { require(it > BigDecimal.ZERO) { "FX rate must be positive" } }
    }
}
