package com.simpletickr.portfolio.model

import java.math.BigDecimal

// Rolls up multiple listings of the same asset (e.g. cross-listed on two exchanges) into one
// asset-level row. totalCostBase/marketValueBase are partial sums: null only when EVERY listing
// lacks the underlying data (no FX rate for cost, no price for market value) — a listing missing
// just one of the two doesn't null out the other.
data class AssetHolding(
    val assetId: Long,
    val assetName: String,
    val totalQuantity: BigDecimal,
    val avgCostBasisBase: BigDecimal?,
    val totalCostBase: BigDecimal?,
    val marketValueBase: BigDecimal?,
    val unrealizedPnlBase: BigDecimal?,
    val unrealizedPnlPct: BigDecimal?,
    val listings: List<HoldingWithValuation>,
)
