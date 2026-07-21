package com.simpletickr.gains

import com.simpletickr.asset.model.Listing
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RealizedGainsCalculatorTest {

    private val from = LocalDate.of(2024, 1, 1)
    private val to = LocalDate.of(2024, 12, 31)

    private val listing = Listing(id = 10L, assetId = 1L, exchange = null, ticker = "AAPL", currency = CurrencyCode("USD"))
    private val listingMap = mapOf(10L to listing)

    private fun buy(id: Long, date: String, qty: String, price: String, fees: String? = null) = Transaction(
        id = id, portfolioId = 1L, listingId = 10L, assetId = 1L,
        type = TransactionType.BUY,
        quantity = BigDecimal(qty), price = BigDecimal(price),
        date = LocalDate.parse(date),
        fees = fees?.let { BigDecimal(it) },
        accountId = 1L,
    )

    private fun sell(id: Long, date: String, qty: String, price: String, fees: String? = null) = Transaction(
        id = id, portfolioId = 1L, listingId = 10L, assetId = 1L,
        type = TransactionType.SELL,
        quantity = BigDecimal(qty), price = BigDecimal(price),
        date = LocalDate.parse(date),
        fees = fees?.let { BigDecimal(it) },
        accountId = 1L,
    )

    private fun transferOut(id: Long, date: String, qty: String, price: String, transferId: Long = 900L) = Transaction(
        id = id, portfolioId = 1L, listingId = 10L, assetId = 1L,
        type = TransactionType.TRANSFER_OUT,
        quantity = BigDecimal(qty), price = BigDecimal(price),
        date = LocalDate.parse(date),
        fees = null,
        accountId = 1L, transferId = transferId,
    )

    private fun transferIn(id: Long, date: String, qty: String, price: String, transferId: Long = 900L) = Transaction(
        id = id, portfolioId = 1L, listingId = 10L, assetId = 1L,
        type = TransactionType.TRANSFER_IN,
        quantity = BigDecimal(qty), price = BigDecimal(price),
        date = LocalDate.parse(date),
        fees = null,
        accountId = 2L, transferId = transferId,
    )

    // ── FIFO ────────────────────────────────────────────────────────────────

    @Test
    fun `FIFO simple gain`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "5", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("750", report.entries[0].proceeds)
        assertBd("500", report.entries[0].costBasis)
        assertBd("250", report.entries[0].gain)
        assertBd("250", report.byCurrency[CurrencyCode("USD")]!!.totalGain)
    }

    @Test
    fun `FIFO with fees`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100", "10"),
            sell(2, "2024-06-01", "5", "150", "5"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        val entry = report.entries[0]
        assertBd("745", entry.proceeds)
        assertBd("5", entry.buyFees)
        assertBd("5", entry.sellFees)
        assertBd("505", entry.costBasis)
        assertBd("240", entry.gain)
    }

    @Test
    fun `FIFO consumes oldest lots first`() {
        val txs = listOf(
            buy(1, "2024-01-01", "5", "100"),
            buy(2, "2024-02-01", "5", "200"),
            sell(3, "2024-06-01", "7", "300"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertBd("2100", report.entries[0].proceeds)
        assertBd("900", report.entries[0].costBasis)
        assertBd("1200", report.entries[0].gain)
    }

    @Test
    fun `FIFO sell before date range still affects lots for later sells`() {
        val txs = listOf(
            buy(1, "2023-01-01", "10", "100"),
            sell(2, "2023-06-01", "5", "150"),
            sell(3, "2024-06-01", "5", "200"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("1000", report.entries[0].proceeds)
        assertBd("500", report.entries[0].costBasis)
        assertBd("500", report.entries[0].gain)
    }

    // ── Average cost ─────────────────────────────────────────────────────────

    @Test
    fun `average cost simple gain`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "5", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertBd("750", report.entries[0].proceeds)
        assertBd("500", report.entries[0].costBasis)
        assertBd("250", report.entries[0].gain)
    }

    @Test
    fun `average cost blends two buy prices`() {
        val txs = listOf(
            buy(1, "2024-01-01", "5", "100"),
            buy(2, "2024-02-01", "5", "200"),
            sell(3, "2024-06-01", "5", "250"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertBd("1250", report.entries[0].proceeds)
        assertBd("750", report.entries[0].costBasis)
        assertBd("500", report.entries[0].gain)
    }

    @Test
    fun `average cost with fees`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100", "10"),
            sell(2, "2024-06-01", "5", "150", "5"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        val entry = report.entries[0]
        assertBd("745", entry.proceeds)
        assertBd("505", entry.costBasis)
        assertBd("240", entry.gain)
    }

    @Test
    fun `report totals aggregate all entries`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-03-01", "3", "120"),
            sell(3, "2024-06-01", "3", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(2, report.entries.size)
        val expectedGain = report.entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.gain }
        assertEquals(0, expectedGain.compareTo(report.byCurrency[CurrencyCode("USD")]!!.totalGain))
    }

    private fun split(id: Long, date: String, ratio: String) = Transaction(
        id = id, portfolioId = 1L, listingId = 10L, assetId = 1L,
        type = TransactionType.SPLIT,
        quantity = BigDecimal(ratio), price = BigDecimal.ZERO,
        date = LocalDate.parse(date),
        fees = null,
        accountId = 1L,
    )

    // ── SPLIT ────────────────────────────────────────────────────────────────

    @Test
    fun `SPLIT alone creates no gain entries`() {
        val txs = listOf(
            buy(1, "2024-01-01", "100", "10"),
            split(2, "2024-06-01", "2"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(0, report.entries.size)
    }

    @Test
    fun `FIFO - sell after 2-for-1 split uses split-adjusted cost basis`() {
        // BUY 100 @ $20 → 2:1 split → 200 lots @ $10 each
        // SELL 100 @ $12 → proceeds = 1200, cost basis = 100 * $10 = $1000, gain = $200
        val txs = listOf(
            buy(1, "2024-01-01", "100", "20"),
            split(2, "2024-06-01", "2"),
            sell(3, "2024-09-01", "100", "12"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("1200", report.entries[0].proceeds)
        assertBd("1000", report.entries[0].costBasis)
        assertBd("200", report.entries[0].gain)
    }

    @Test
    fun `AVCO - sell after 2-for-1 split uses split-adjusted cost basis`() {
        val txs = listOf(
            buy(1, "2024-01-01", "100", "20"),
            split(2, "2024-06-01", "2"),
            sell(3, "2024-09-01", "100", "12"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertEquals(1, report.entries.size)
        assertBd("1200", report.entries[0].proceeds)
        assertBd("1000", report.entries[0].costBasis)
        assertBd("200", report.entries[0].gain)
    }

    @Test
    fun `FIFO - pre-split buy and post-split buy, sell uses correct lots`() {
        // BUY 100 @ $20 → 2:1 split → adjusted to 200 @ $10
        // BUY 50 @ $12 (after split, already post-split price) → 50 @ $12
        // SELL 150: first 150 from pre-split lot (all 150 @ $10), wait no: we have 200 @ $10 then 50 @ $12
        // SELL 150 → 150 × $10 = $1500 cost, proceeds = 150 × $15 = $2250, gain = $750
        val txs = listOf(
            buy(1, "2024-01-01", "100", "20"),
            split(2, "2024-06-01", "2"),
            buy(3, "2024-07-01", "50", "12"),
            sell(4, "2024-10-01", "150", "15"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("2250", report.entries[0].proceeds)
        assertBd("1500", report.entries[0].costBasis)
        assertBd("750", report.entries[0].gain)
    }

    private fun assertBd(expected: String, actual: BigDecimal, message: String = "") =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "$message expected $expected but was $actual")

    // ── TRANSFER ─────────────────────────────────────────────────────────────

    @Test
    fun `FIFO - TRANSFER_OUT alone creates no gain entry`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            transferOut(2, "2024-03-01", "4", "999"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(0, report.entries.size)
    }

    @Test
    fun `AVCO - TRANSFER_OUT alone creates no gain entry`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            transferOut(2, "2024-03-01", "4", "999"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertEquals(0, report.entries.size)
    }

    @Test
    fun `FIFO - TRANSFER_OUT reduces remaining lots for a later real SELL`() {
        // BUY 10 @ 100 -> lot of 10 @ 100. TRANSFER_OUT 4 consumes 4 units from that lot (no entry,
        // transfer's own price is irrelevant to lot consumption). SELL the remaining 6 @ 100 cost basis.
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            transferOut(2, "2024-03-01", "4", "999"),
            sell(3, "2024-06-01", "6", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("900", report.entries[0].proceeds)
        assertBd("600", report.entries[0].costBasis)
        assertBd("300", report.entries[0].gain)
    }

    @Test
    fun `AVCO - TRANSFER_OUT reduces AssetState for a later real SELL`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            transferOut(2, "2024-03-01", "4", "999"),
            sell(3, "2024-06-01", "6", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertEquals(1, report.entries.size)
        assertBd("900", report.entries[0].proceeds)
        assertBd("600", report.entries[0].costBasis)
        assertBd("300", report.entries[0].gain)
    }

    @Test
    fun `TRANSFER_IN behaves like a BUY - lands a fresh lot at its frozen price, no gain entry`() {
        val txs = listOf(
            transferIn(1, "2024-01-01", "0.5", "30000"),
        )
        val fifo = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.FIFO, from, to)
        val avco = RealizedGainsCalculator.compute(txs, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertEquals(0, fifo.entries.size)
        assertEquals(0, avco.entries.size)
    }

    @Test
    fun `FIFO - documented limitation - transferred lot sells against WAC-derived basis, not original per-lot cost`() {
        // Source portfolio: BUY 1 BTC @ 10,000 (lot A), BUY 1 BTC @ 50,000 (lot B). Correct FIFO would
        // tag a transfer of 0.5 BTC as "from lot A, cost 10,000/unit." RecordTransferUseCase instead
        // computes currentAverageCost = 30,000 and stamps the TRANSFER_IN leg with that price - this
        // test operates directly on the destination-side TRANSFER_IN transaction (as the use case would
        // produce it) to document that a later sale computes gain against 30,000/unit, not 10,000.
        val destinationTxs = listOf(
            transferIn(1, "2024-03-01", "0.5", "30000"),
            sell(2, "2024-06-01", "0.5", "40000"),
        )
        val report = RealizedGainsCalculator.compute(destinationTxs, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("20000", report.entries[0].proceeds)
        assertBd("15000", report.entries[0].costBasis, "cost basis reflects the frozen WAC (30,000/unit), not lot A's original 10,000/unit")
        assertBd("5000", report.entries[0].gain)
    }
}
