package com.simpletickr.portfolio

import com.simpletickr.portfolio.model.AssetHolding
import com.simpletickr.portfolio.model.HoldingWithValuation
import com.simpletickr.portfolio.model.PortfolioValuationSummary
import com.simpletickr.shared.CurrencyCode
import java.math.BigDecimal
import java.math.RoundingMode

object PortfolioValuationCalculator {

    // Sums whatever values are present; null only if the list is empty or every value is null —
    // one listing/asset missing data never nulls out the contribution of the others.
    private fun partialSum(values: List<BigDecimal?>): BigDecimal? =
        values.filterNotNull().takeIf { it.isNotEmpty() }
            ?.fold(BigDecimal.ZERO) { acc, v -> acc + v }

    fun rollUpByAsset(listingValuations: List<HoldingWithValuation>, baseCurrency: CurrencyCode): List<AssetHolding> =
        listingValuations
            .groupBy { it.holding.assetId }
            .map { (_, items) ->
                val first = items.first().holding
                val totalQty = items.sumOf { it.holding.quantity }

                val allCostBase = items.map { hwv ->
                    val fx = hwv.fxUsed
                    if (hwv.holding.currency == baseCurrency) hwv.holding.totalCostLocal
                    else fx?.let { hwv.holding.totalCostLocal / it }
                }
                val totalCostBase = partialSum(allCostBase)
                val avgCostBasisBase = totalCostBase?.let {
                    if (totalQty > BigDecimal.ZERO) it.divide(totalQty, 10, RoundingMode.HALF_UP) else null
                }

                // Same conservative trade-off as PortfolioValuationSummary.totalUnrealizedPnlPct
                // below, one level down: if this asset has multiple listings and only some are
                // priced, totalCostBase still includes the unpriced listing's cost (cost doesn't
                // need a price to be known) while totalMarketValue doesn't include its value — so
                // pnl can look artificially negative for a partially-priced multi-listing asset.
                // Deliberate, not a bug: never overstates the asset's value.
                val totalMarketValue = partialSum(items.map { it.marketValueBase })
                val totalUnrealizedPnl = totalMarketValue?.let { mv -> totalCostBase?.let { cb -> mv - cb } }
                val totalUnrealizedPct = totalUnrealizedPnl?.let { pnl ->
                    totalCostBase?.let { cb ->
                        if (cb > BigDecimal.ZERO) pnl.divide(cb, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100")) else null
                    }
                }

                AssetHolding(
                    assetId = first.assetId,
                    assetName = first.assetName,
                    totalQuantity = totalQty,
                    avgCostBasisBase = avgCostBasisBase,
                    totalCostBase = totalCostBase,
                    marketValueBase = totalMarketValue,
                    unrealizedPnlBase = totalUnrealizedPnl,
                    unrealizedPnlPct = totalUnrealizedPct,
                    listings = items,
                )
            }

    fun summarize(assetHoldings: List<AssetHolding>): PortfolioValuationSummary {
        val totalCostBase = assetHoldings.sumOf { it.totalCostBase ?: BigDecimal.ZERO }
        val totalMarketValueBase = partialSum(assetHoldings.map { it.marketValueBase })
        val totalUnrealizedPnlBase = partialSum(assetHoldings.map { it.unrealizedPnlBase })

        // Percentage is computed against the FULL cost basis (including excluded holdings), not
        // just the priced portion. E.g. holding A (cost 100, value 150, priced) + holding B (cost
        // 100, no price, excluded) -> reported gain = +50, gain% = 50/200 = 25%, not 50/100 = 50%.
        // This under-states rather than over-states the percentage, and avoids implying knowledge
        // about B's current value that doesn't exist.
        val totalUnrealizedPnlPct = totalUnrealizedPnlBase?.let {
            if (totalCostBase > BigDecimal.ZERO)
                it.divide(totalCostBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
            else null
        }

        val excluded = assetHoldings.filter { it.marketValueBase == null }

        return PortfolioValuationSummary(
            totalCostBase = totalCostBase,
            totalMarketValueBase = totalMarketValueBase,
            totalUnrealizedPnlBase = totalUnrealizedPnlBase,
            totalUnrealizedPnlPct = totalUnrealizedPnlPct,
            excludedHoldingCount = excluded.size,
            excludedHoldingNames = excluded.map { it.assetName },
        )
    }
}
