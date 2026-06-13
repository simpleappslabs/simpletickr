package com.simpletickr.portfolio

import com.simpletickr.asset.Asset
import com.simpletickr.asset.AssetType
import com.simpletickr.transaction.Transaction
import com.simpletickr.transaction.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RealizedGainsCalculatorTest {

    private val from = LocalDate.of(2024, 1, 1)
    private val to = LocalDate.of(2024, 12, 31)

    private val apple = Asset(1L, "AAPL", "Apple Inc.", AssetType.STOCK, "USD", null)
    private val assetMap = mapOf(1L to apple)

    private fun buy(id: Long, date: String, qty: String, price: String, fees: String? = null) = Transaction(
        id = id, portfolioId = 1L, assetId = 1L,
        type = TransactionType.BUY,
        quantity = BigDecimal(qty), price = BigDecimal(price),
        date = LocalDate.parse(date),
        fees = fees?.let { BigDecimal(it) },
    )

    private fun sell(id: Long, date: String, qty: String, price: String, fees: String? = null) = Transaction(
        id = id, portfolioId = 1L, assetId = 1L,
        type = TransactionType.SELL,
        quantity = BigDecimal(qty), price = BigDecimal(price),
        date = LocalDate.parse(date),
        fees = fees?.let { BigDecimal(it) },
    )

    // ── FIFO ────────────────────────────────────────────────────────────────

    @Test
    fun `FIFO simple gain`() {
        // Buy 10 @ 100, sell 5 @ 150  → gain = (5*150) - (5*100) = 250
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "5", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("750", report.entries[0].proceeds)
        assertBd("500", report.entries[0].costBasis)
        assertBd("250", report.entries[0].gain)
        assertBd("250", report.totalGain)
    }

    @Test
    fun `FIFO with fees`() {
        // Buy 10 @ 100, fees 10 → feePerUnit = 1 → costBasisPerUnit = 101
        // Sell 5 @ 150, fees 5 → proceeds = 750 - 5 = 745
        // costBasis = 5 * 101 = 505
        // gain = 745 - 505 = 240
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100", "10"),
            sell(2, "2024-06-01", "5", "150", "5"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.FIFO, from, to)
        val entry = report.entries[0]
        assertBd("745", entry.proceeds)
        assertBd("5", entry.buyFees)
        assertBd("5", entry.sellFees)
        assertBd("505", entry.costBasis)
        assertBd("240", entry.gain)
    }

    @Test
    fun `FIFO consumes oldest lots first`() {
        // Lot 1: buy 5 @ 100
        // Lot 2: buy 5 @ 200
        // Sell 7 @ 300 → consumes all of lot1 (5) + 2 from lot2
        // purchaseValue = 5*100 + 2*200 = 900
        // proceeds = 7*300 = 2100
        // gain = 2100 - 900 = 1200
        val txs = listOf(
            buy(1, "2024-01-01", "5", "100"),
            buy(2, "2024-02-01", "5", "200"),
            sell(3, "2024-06-01", "7", "300"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.FIFO, from, to)
        assertBd("2100", report.entries[0].proceeds)
        assertBd("900", report.entries[0].costBasis)
        assertBd("1200", report.entries[0].gain)
    }

    @Test
    fun `FIFO sell before date range still affects lots for later sells`() {
        // Buy 10 @ 100 in 2023, sell 5 @ 150 in 2023 (outside range), sell 5 @ 200 in 2024
        // The 2023 sell should consume 5 lots; 2024 sell uses remaining 5
        val txs = listOf(
            buy(1, "2023-01-01", "10", "100"),
            sell(2, "2023-06-01", "5", "150"),
            sell(3, "2024-06-01", "5", "200"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("1000", report.entries[0].proceeds)
        assertBd("500", report.entries[0].costBasis)
        assertBd("500", report.entries[0].gain)
    }

    // ── Average cost ─────────────────────────────────────────────────────────

    @Test
    fun `average cost simple gain`() {
        // Buy 10 @ 100 → avg = 100
        // Sell 5 @ 150 → costBasis = 5*100 = 500, proceeds = 750, gain = 250
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "5", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.AVERAGE_COST, from, to)
        assertBd("750", report.entries[0].proceeds)
        assertBd("500", report.entries[0].costBasis)
        assertBd("250", report.entries[0].gain)
    }

    @Test
    fun `average cost blends two buy prices`() {
        // Buy 5 @ 100, buy 5 @ 200 → avg = 150
        // Sell 5 @ 250 → costBasis = 5*150 = 750, proceeds = 1250, gain = 500
        val txs = listOf(
            buy(1, "2024-01-01", "5", "100"),
            buy(2, "2024-02-01", "5", "200"),
            sell(3, "2024-06-01", "5", "250"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.AVERAGE_COST, from, to)
        assertBd("1250", report.entries[0].proceeds)
        assertBd("750", report.entries[0].costBasis)
        assertBd("500", report.entries[0].gain)
    }

    @Test
    fun `average cost with fees`() {
        // Buy 10 @ 100, fees 10 → totalPurchaseValue=1000, totalBuyFees=10
        // avgPrice=100, avgFee=1, avgCostBasis=101
        // Sell 5 @ 150, fees 5 → proceeds = 750-5=745, costBasis=5*101=505, gain=240
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100", "10"),
            sell(2, "2024-06-01", "5", "150", "5"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.AVERAGE_COST, from, to)
        val entry = report.entries[0]
        assertBd("745", entry.proceeds)
        assertBd("505", entry.costBasis)
        assertBd("240", entry.gain)
    }

    @Test
    fun `report totals aggregate all entries`() {
        // Two sells in range
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-03-01", "3", "120"),
            sell(3, "2024-06-01", "3", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, assetMap, RealizationMethod.FIFO, from, to)
        assertEquals(2, report.entries.size)
        val expectedGain = report.entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.gain }
        assertEquals(expectedGain, report.totalGain)
    }

    private fun bd(v: String) = BigDecimal(v)

    private fun assertBd(expected: String, actual: BigDecimal, message: String = "") =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "$message expected $expected but was $actual")
}
