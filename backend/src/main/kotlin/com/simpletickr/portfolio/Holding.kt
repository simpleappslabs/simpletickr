package com.simpletickr.portfolio

import com.simpletickr.shared.CurrencyCode
import java.math.BigDecimal

// Pure WAC aggregation — one row per (assetId, listingId).
// No valuation, no FX. Derived from transactions by HoldingService.
data class Holding(
    val assetId: Long,
    val assetName: String,
    val listingId: Long,
    val exchange: String?,
    val ticker: String,
    val currency: CurrencyCode,
    val quantity: BigDecimal,
    val avgCostLocal: BigDecimal,
    val totalCostLocal: BigDecimal,
)
