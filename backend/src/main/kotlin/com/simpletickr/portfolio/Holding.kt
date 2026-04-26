package com.simpletickr.portfolio

import java.math.BigDecimal

data class Holding(
    val assetId: Long,
    val ticker: String,
    val name: String,
    val quantity: BigDecimal,
    val avgCostBasis: BigDecimal,
    val totalCost: BigDecimal,
    val unrealizedGain: BigDecimal?,
)