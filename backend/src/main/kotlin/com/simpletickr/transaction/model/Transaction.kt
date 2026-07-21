package com.simpletickr.transaction.model

import com.simpletickr.fx.model.FxRateSource
import java.math.BigDecimal
import java.time.LocalDate

enum class TransactionType { BUY, SELL, SPLIT, TRANSFER_OUT, TRANSFER_IN }

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
    val accountId: Long,
    val notes: String? = null,
    val tradeId: Long? = null,
    val transferId: Long? = null,
    // In-kind fee (e.g. crypto gas) paid out of the transferred asset itself, set only on the TRANSFER_OUT leg.
    // Fiat/cash transfer fees are not modeled here.
    val assetFeeQuantity: BigDecimal? = null,
) {
    init {
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
        require(price >= BigDecimal.ZERO) { "Price must not be negative" }
        fees?.let { require(it >= BigDecimal.ZERO) { "Fees must not be negative" } }
        fxRate?.let { require(it > BigDecimal.ZERO) { "FX rate must be positive" } }
        assetFeeQuantity?.let {
            require(it >= BigDecimal.ZERO) { "Asset fee quantity must not be negative" }
            require(it < quantity) { "Asset fee quantity must be less than quantity" }
        }
    }
}
