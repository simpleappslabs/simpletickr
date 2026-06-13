package com.simpletickr.transaction

import java.math.BigDecimal
import java.time.LocalDate

enum class TransactionType { BUY, SELL }

data class Transaction(
    val id: Long,
    val portfolioId: Long,
    val assetId: Long,
    val type: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
) {
    init {
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
        require(price >= BigDecimal.ZERO) { "Price must not be negative" }
        fees?.let { require(it >= BigDecimal.ZERO) { "Fees must not be negative" } }
    }
}
