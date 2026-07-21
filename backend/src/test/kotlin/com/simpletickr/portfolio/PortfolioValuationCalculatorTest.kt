package com.simpletickr.portfolio

import com.simpletickr.portfolio.model.AssetHolding
import com.simpletickr.portfolio.model.Holding
import com.simpletickr.portfolio.model.HoldingWithValuation
import com.simpletickr.shared.CurrencyCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PortfolioValuationCalculatorTest {

    private val eur = CurrencyCode("EUR")

    private fun holding(
        assetId: Long, assetName: String, listingId: Long, qty: String, totalCostLocal: String,
        currency: CurrencyCode = eur,
    ) = Holding(
        assetId = assetId, assetName = assetName, listingId = listingId, exchange = null,
        ticker = assetName, currency = currency,
        quantity = BigDecimal(qty), avgCostLocal = BigDecimal(totalCostLocal).divide(BigDecimal(qty)),
        totalCostLocal = BigDecimal(totalCostLocal),
    )

    private fun valuation(
        holding: Holding, marketValueBase: String? = null, fxUsed: String? = null,
    ) = HoldingWithValuation(
        holding = holding,
        marketValueLocal = marketValueBase?.let { BigDecimal(it) },
        marketValueBase = marketValueBase?.let { BigDecimal(it) },
        unrealizedPnlBase = null,
        unrealizedPnlPct = null,
        fxUsed = fxUsed?.let { BigDecimal(it) },
    )

    private fun assertBd(expected: String, actual: BigDecimal?, message: String = "") =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "$message expected $expected but was $actual")

    // ── rollUpByAsset ──────────────────────────────────────────────────────────

    @Test
    fun `rollUpByAsset - all listings priced sums normally`() {
        val listings = listOf(
            valuation(holding(1, "Acme", 10, "10", "1000"), marketValueBase = "1200"),
            valuation(holding(1, "Acme", 11, "5", "600"), marketValueBase = "700"),
        )
        val result = PortfolioValuationCalculator.rollUpByAsset(listings, eur)

        assertEquals(1, result.size)
        val asset = result[0]
        assertBd("15", asset.totalQuantity)
        assertBd("1600", asset.totalCostBase)
        assertBd("1900", asset.marketValueBase)
        assertBd("300", asset.unrealizedPnlBase)
        assertBd("18.75", asset.unrealizedPnlPct)
    }

    @Test
    fun `rollUpByAsset - one of two listings missing a price still reports a partial market value`() {
        val listings = listOf(
            valuation(holding(1, "Acme", 10, "10", "1000"), marketValueBase = "1200"),
            valuation(holding(1, "Acme", 11, "5", "600"), marketValueBase = null),
        )
        val result = PortfolioValuationCalculator.rollUpByAsset(listings, eur)

        val asset = result[0]
        assertBd("1600", asset.totalCostBase) // cost is fully known regardless of price
        assertBd("1200", asset.marketValueBase) // only the priced listing contributes
    }

    @Test
    fun `rollUpByAsset - all listings missing FX rate leaves cost null`() {
        val usd = CurrencyCode("USD")
        val listings = listOf(
            valuation(holding(1, "Acme", 10, "10", "1000", currency = usd), fxUsed = null),
            valuation(holding(1, "Acme", 11, "5", "600", currency = usd), fxUsed = null),
        )
        val result = PortfolioValuationCalculator.rollUpByAsset(listings, eur)

        val asset = result[0]
        assertEquals(null, asset.totalCostBase)
        assertEquals(null, asset.avgCostBasisBase)
    }

    // ── summarize ──────────────────────────────────────────────────────────────

    private fun assetHolding(
        assetId: Long, assetName: String, totalCostBase: String, marketValueBase: String?,
    ) = AssetHolding(
        assetId = assetId, assetName = assetName,
        totalQuantity = BigDecimal.ONE, avgCostBasisBase = BigDecimal(totalCostBase),
        totalCostBase = BigDecimal(totalCostBase),
        marketValueBase = marketValueBase?.let { BigDecimal(it) },
        unrealizedPnlBase = marketValueBase?.let { BigDecimal(it) - BigDecimal(totalCostBase) },
        unrealizedPnlPct = null,
        listings = emptyList(),
    )

    @Test
    fun `summarize - all assets priced sums normally with no exclusions`() {
        val assets = listOf(
            assetHolding(1, "Acme", "1000", "1200"),
            assetHolding(2, "Beta", "500", "550"),
        )
        val summary = PortfolioValuationCalculator.summarize(assets)

        assertBd("1500", summary.totalCostBase)
        assertBd("1750", summary.totalMarketValueBase!!)
        assertBd("250", summary.totalUnrealizedPnlBase!!)
        assertEquals(0, summary.excludedHoldingCount)
        assertEquals(emptyList<String>(), summary.excludedHoldingNames)
    }

    @Test
    fun `summarize - one of several assets missing a price is excluded, not nulling the total`() {
        val assets = listOf(
            assetHolding(1, "Acme", "1000", "1200"),
            assetHolding(2, "Request Network", "500", null),
        )
        val summary = PortfolioValuationCalculator.summarize(assets)

        assertBd("1500", summary.totalCostBase) // Request Network's cost is still counted
        assertBd("1200", summary.totalMarketValueBase!!) // only Acme's value
        assertEquals(1, summary.excludedHoldingCount)
        assertEquals(listOf("Request Network"), summary.excludedHoldingNames)
    }

    @Test
    fun `summarize - percentage is computed against the full cost basis, not just the priced portion`() {
        // Holding A: cost 100, value 150 (priced). Holding B: cost 100, no price (excluded).
        // gain = +50 (A's alone); gain% = 50 / 200 = 25%, not 50 / 100 = 50%.
        val assets = listOf(
            assetHolding(1, "A", "100", "150"),
            assetHolding(2, "B", "100", null),
        )
        val summary = PortfolioValuationCalculator.summarize(assets)

        assertBd("200", summary.totalCostBase)
        assertBd("50", summary.totalUnrealizedPnlBase!!)
        assertBd("25", summary.totalUnrealizedPnlPct!!)
    }

    @Test
    fun `summarize - all assets missing a price yields null totals but a full cost basis`() {
        val assets = listOf(
            assetHolding(1, "A", "100", null),
            assetHolding(2, "B", "100", null),
        )
        val summary = PortfolioValuationCalculator.summarize(assets)

        assertBd("200", summary.totalCostBase)
        assertEquals(null, summary.totalMarketValueBase)
        assertEquals(null, summary.totalUnrealizedPnlBase)
        assertEquals(null, summary.totalUnrealizedPnlPct)
        assertEquals(2, summary.excludedHoldingCount)
    }
}
