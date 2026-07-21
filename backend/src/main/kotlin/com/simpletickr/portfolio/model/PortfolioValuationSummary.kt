package com.simpletickr.portfolio.model

import java.math.BigDecimal

// Portfolio-level rollup of AssetHolding valuations. Explicitly reports what it excluded from
// marketValueBase/unrealizedPnl* rather than leaving consumers to infer exclusion from a null —
// null can mean many things over time (no price, stale price, FX outage, ...); this says why.
data class PortfolioValuationSummary(
    val totalCostBase: BigDecimal,
    val totalMarketValueBase: BigDecimal?,
    val totalUnrealizedPnlBase: BigDecimal?,
    val totalUnrealizedPnlPct: BigDecimal?,
    val excludedHoldingCount: Int,
    val excludedHoldingNames: List<String>,
)
