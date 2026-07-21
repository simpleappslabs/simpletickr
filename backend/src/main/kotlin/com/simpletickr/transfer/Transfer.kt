package com.simpletickr.transfer

import java.math.BigDecimal
import java.time.LocalDate

// Moves custody of a quantity of one asset from one account to another, within a single
// portfolio. It has no price and no cost-basis field: a transfer has no effect on
// portfolio-level inventory — only its fee (if any) does, since that quantity is genuinely
// lost. Cost basis is always derived by replaying transactions/transfer fees, never frozen here.
data class Transfer(
    val id: Long,
    val portfolioId: Long,
    val listingId: Long,
    val assetId: Long,
    val quantity: BigDecimal,
    val assetFeeQuantity: BigDecimal? = null,
    val date: LocalDate,
    val sourceAccountId: Long,
    val destinationAccountId: Long,
    val notes: String? = null,
) {
    init {
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
        require(sourceAccountId != destinationAccountId) { "Source and destination accounts must be different" }
        assetFeeQuantity?.let {
            require(it >= BigDecimal.ZERO) { "Asset fee quantity must not be negative" }
            require(it < quantity) { "Asset fee quantity must be less than quantity" }
        }
    }
}
