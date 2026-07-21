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

    private fun transferFee(transferId: Long, date: String, feeQty: String, assetId: Long = 1L) =
        RealizedGainsCalculator.TransferFeeEvent(
            transferId = transferId, assetId = assetId, date = LocalDate.parse(date), feeQuantity = BigDecimal(feeQty),
        )

    // ── FIFO ────────────────────────────────────────────────────────────────

    @Test
    fun `FIFO simple gain`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "5", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
        val entry = report.entries[0]
        assertBd("2100", entry.proceeds)
        assertBd("900", entry.costBasis)
        assertBd("1200", entry.gain)

        assertEquals(2, entry.lots.size)
        assertEquals(LocalDate.of(2024, 1, 1), entry.lots[0].acquisitionDate)
        assertBd("5", entry.lots[0].quantity)
        assertBd("100", entry.lots[0].pricePerUnit)
        assertEquals(LocalDate.of(2024, 2, 1), entry.lots[1].acquisitionDate)
        assertBd("2", entry.lots[1].quantity)
        assertBd("200", entry.lots[1].pricePerUnit)
        assertLotsReconcile(entry)
    }

    @Test
    fun `FIFO - lot quantities reconcile even with a partial lot in the middle`() {
        // BUY 10 @ 100, BUY 10 @ 200, SELL 15 -> 10 @ 100 (fully drains the oldest lot) + 5 @ 200
        // (partial second lot). Quantity reconciliation catches partial-lot bugs a cost-basis-only
        // check could mask.
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            buy(2, "2024-02-01", "10", "200"),
            sell(3, "2024-06-01", "15", "300"),
        )
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
        val entry = report.entries[0]

        assertEquals(2, entry.lots.size)
        assertBd("10", entry.lots[0].quantity)
        assertBd("100", entry.lots[0].pricePerUnit)
        assertBd("5", entry.lots[1].quantity)
        assertBd("200", entry.lots[1].pricePerUnit)
        assertLotsReconcile(entry)
    }

    @Test
    fun `FIFO - a sale exactly draining one lot reports exactly one lot`() {
        val txs = listOf(
            buy(1, "2024-01-01", "5", "100"),
            sell(2, "2024-06-01", "5", "150"),
        )
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
        val entry = report.entries[0]

        assertEquals(1, entry.lots.size)
        assertBd("5", entry.lots[0].quantity)
        assertLotsReconcile(entry)
    }

    @Test
    fun `FIFO sell before date range still affects lots for later sells`() {
        val txs = listOf(
            buy(1, "2023-01-01", "10", "100"),
            sell(2, "2023-06-01", "5", "150"),
            sell(3, "2024-06-01", "5", "200"),
        )
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.AVERAGE_COST, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.AVERAGE_COST, from, to)
        val entry = report.entries[0]
        assertBd("1250", entry.proceeds)
        assertBd("750", entry.costBasis)
        assertBd("500", entry.gain)
        // AVCO has no discrete lots - just one blended pool per asset - so this is always empty.
        assertEquals(0, entry.lots.size)
    }

    @Test
    fun `average cost with fees`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100", "10"),
            sell(2, "2024-06-01", "5", "150", "5"),
        )
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.AVERAGE_COST, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        val entry = report.entries[0]
        assertBd("1200", entry.proceeds)
        assertBd("1000", entry.costBasis)
        assertBd("200", entry.gain)

        // The reported lot keeps the ORIGINAL acquisition date (a split doesn't change when the
        // shares were bought), but its pricePerUnit reflects the post-split-adjusted price.
        assertEquals(1, entry.lots.size)
        assertEquals(LocalDate.of(2024, 1, 1), entry.lots[0].acquisitionDate)
        assertBd("100", entry.lots[0].quantity)
        assertBd("10", entry.lots[0].pricePerUnit)
        assertLotsReconcile(entry)
    }

    @Test
    fun `AVCO - sell after 2-for-1 split uses split-adjusted cost basis`() {
        val txs = listOf(
            buy(1, "2024-01-01", "100", "20"),
            split(2, "2024-06-01", "2"),
            sell(3, "2024-09-01", "100", "12"),
        )
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.AVERAGE_COST, from, to)
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
        val report = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("2250", report.entries[0].proceeds)
        assertBd("1500", report.entries[0].costBasis)
        assertBd("750", report.entries[0].gain)
    }

    private fun assertBd(expected: String, actual: BigDecimal, message: String = "") =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "$message expected $expected but was $actual")

    // The debugging value of lot-detail reporting hinges on this holding exactly: every lot's
    // quantity/costBasis/buyFees must sum back to the entry's own fields.
    private fun assertLotsReconcile(entry: RealizedGainEntry) {
        val totalQty = entry.lots.fold(BigDecimal.ZERO) { acc, l -> acc + l.quantity }
        val totalCost = entry.lots.fold(BigDecimal.ZERO) { acc, l -> acc + l.costBasis }
        val totalFees = entry.lots.fold(BigDecimal.ZERO) { acc, l -> acc + l.buyFees }
        assertEquals(0, entry.quantity.compareTo(totalQty), "lot quantities should sum to entry quantity")
        assertEquals(0, entry.costBasis.compareTo(totalCost), "lot cost basis should sum to entry cost basis")
        assertEquals(0, entry.buyFees.compareTo(totalFees), "lot buy fees should sum to entry buy fees")
    }

    // ── TRANSFER ─────────────────────────────────────────────────────────────
    // A transfer moves custody, not portfolio inventory: it never appears here as a whole event
    // with a price. Only its in-kind fee (if any) is a genuine disposal, fed in as a TransferFeeEvent.

    @Test
    fun `FIFO and AVCO - a transfer with no fee is a pure no-op, identical to no transfer at all`() {
        // A transfer with no in-kind fee never reaches the calculator as an event at all (the
        // caller filters it out before building the transferFees list) - passing an empty list is
        // exactly equivalent to there having been no transfer.
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "6", "150"),
        )
        val fifo = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.FIFO, from, to)
        val avco = RealizedGainsCalculator.compute(txs, emptyList(), listingMap, RealizationMethod.AVERAGE_COST, from, to)

        assertEquals(1, fifo.entries.size)
        assertBd("900", fifo.entries[0].proceeds)
        assertBd("600", fifo.entries[0].costBasis)
        assertBd("300", fifo.entries[0].gain)

        assertEquals(1, avco.entries.size)
        assertBd("900", avco.entries[0].proceeds)
        assertBd("600", avco.entries[0].costBasis)
        assertBd("300", avco.entries[0].gain)
    }

    @Test
    fun `FIFO and AVCO - a transfer fee never emits a RealizedGainEntry, regardless of size`() {
        // No entry means no lots field to populate either - the lots consumed by the fee are
        // simply discarded along with the rest of that consumption, same as before this feature.
        val txs = listOf(buy(1, "2024-01-01", "10", "100"))
        val fees = listOf(transferFee(900L, "2024-03-01", "9.99"))

        val fifo = RealizedGainsCalculator.compute(txs, fees, listingMap, RealizationMethod.FIFO, from, to)
        val avco = RealizedGainsCalculator.compute(txs, fees, listingMap, RealizationMethod.AVERAGE_COST, from, to)

        assertEquals(0, fifo.entries.size)
        assertEquals(0, avco.entries.size)
    }

    @Test
    fun `FIFO - transfer fee reduces remaining lots for a later real SELL`() {
        // BUY 10 @ 100 -> lot of 10 @ 100. A transfer fee of 4 units consumes 4 units from that lot
        // (no entry - a fee is a disposal, not a taxable event). SELL the remaining 6 @ 100 cost basis.
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "6", "150"),
        )
        val fees = listOf(transferFee(900L, "2024-03-01", "4"))
        val report = RealizedGainsCalculator.compute(txs, fees, listingMap, RealizationMethod.FIFO, from, to)
        assertEquals(1, report.entries.size)
        assertBd("900", report.entries[0].proceeds)
        assertBd("600", report.entries[0].costBasis)
        assertBd("300", report.entries[0].gain)
    }

    @Test
    fun `AVCO - transfer fee reduces AssetState for a later real SELL`() {
        val txs = listOf(
            buy(1, "2024-01-01", "10", "100"),
            sell(2, "2024-06-01", "6", "150"),
        )
        val fees = listOf(transferFee(900L, "2024-03-01", "4"))
        val report = RealizedGainsCalculator.compute(txs, fees, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertEquals(1, report.entries.size)
        assertBd("900", report.entries[0].proceeds)
        assertBd("600", report.entries[0].costBasis)
        assertBd("300", report.entries[0].gain)
    }

    @Test
    fun `AVCO - a transfer fee interleaved between two buys replays in date order, not id order`() {
        // The fee's transferId (900) is deliberately far larger than either buy's id (1, 2) to prove
        // the merge sorts purely by date. Comparing Transaction.id against Transfer.id directly (they
        // are separate autoincrement sequences) would wrongly push this fee after both buys, blending
        // a different (wrong) average: correct chronological replay gives 800 cost basis below: a
        // naive id-ordered replay would instead give 720 (avg 100 -> blended-then-fee-consumed 180).
        val txs = listOf(
            buy(1, "2024-01-01", "6", "100"),
            buy(2, "2024-04-01", "4", "300"),
            sell(3, "2024-06-01", "4", "500"),
        )
        val fees = listOf(transferFee(900L, "2024-02-01", "2"))
        val report = RealizedGainsCalculator.compute(txs, fees, listingMap, RealizationMethod.AVERAGE_COST, from, to)
        assertEquals(1, report.entries.size)
        assertBd("2000", report.entries[0].proceeds)
        assertBd("800", report.entries[0].costBasis)
        assertBd("1200", report.entries[0].gain)
    }
}
