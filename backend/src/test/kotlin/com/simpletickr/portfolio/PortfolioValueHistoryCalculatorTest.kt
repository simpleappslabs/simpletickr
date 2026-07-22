package com.simpletickr.portfolio

import com.simpletickr.asset.model.Listing
import com.simpletickr.fx.model.FxRate
import com.simpletickr.price.model.PricePoint
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class PortfolioValueHistoryCalculatorTest {

    private val eur = CurrencyCode("EUR")
    private val usd = CurrencyCode("USD")

    private val eurListing = Listing(id = 1L, assetId = 1L, exchange = null, ticker = "TST", currency = eur)
    private val usdListing = Listing(id = 2L, assetId = 2L, exchange = null, ticker = "USD_STK", currency = usd)

    private var nextTxId = 1L

    private fun buy(
        listingId: Long = eurListing.id, qty: String, price: String, date: LocalDate, fxRate: String? = null,
    ) = Transaction(
        id = nextTxId++, portfolioId = 1L, listingId = listingId, assetId = 1L,
        type = TransactionType.BUY, quantity = BigDecimal(qty), price = BigDecimal(price),
        date = date, fees = null, fxRate = fxRate?.let { BigDecimal(it) }, accountId = 1L,
    )

    private fun sell(
        listingId: Long = eurListing.id, qty: String, price: String, date: LocalDate, fxRate: String? = null,
    ) = Transaction(
        id = nextTxId++, portfolioId = 1L, listingId = listingId, assetId = 1L,
        type = TransactionType.SELL, quantity = BigDecimal(qty), price = BigDecimal(price),
        date = date, fees = null, fxRate = fxRate?.let { BigDecimal(it) }, accountId = 1L,
    )

    private fun prices(listingId: Long, vararg points: Pair<LocalDate, String>) =
        listingId to points.map { (date, price) -> PricePoint(date, BigDecimal(price)) }

    private fun fxRates(quote: CurrencyCode, vararg points: Pair<LocalDate, String>) =
        quote to points.map { (date, rate) -> FxRate(eur, quote, date, BigDecimal(rate)) }

    private fun compute(
        transactions: List<Transaction>,
        from: LocalDate,
        to: LocalDate,
        listings: List<Listing> = listOf(eurListing),
        priceHistory: Map<Long, List<PricePoint>> = emptyMap(),
        fxRateHistory: Map<CurrencyCode, List<FxRate>> = emptyMap(),
    ) = PortfolioValueHistoryCalculator.compute(
        transactions = transactions,
        listingMap = listings.associateBy { it.id },
        priceHistory = priceHistory,
        fxRateHistory = fxRateHistory,
        baseCurrency = eur,
        from = from,
        to = to,
    )

    private fun assertBd(expected: String, actual: BigDecimal?) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")

    @Test
    fun `returns null points when no transactions`() {
        val result = compute(emptyList(), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3))

        assertEquals(3, result.size)
        assertEquals(true, result.all { it.value == null })
        assertEquals(true, result.all { it.invested == null })
    }

    @Test
    fun `returns correct value and invested for base-currency holding`() {
        val buyDate = LocalDate.of(2024, 1, 1)
        val result = compute(
            transactions = listOf(buy(qty = "10", price = "100.00", date = buyDate)),
            from = buyDate, to = LocalDate.of(2024, 1, 3),
            priceHistory = mapOf(prices(eurListing.id,
                LocalDate.of(2024, 1, 1) to "100.00",
                LocalDate.of(2024, 1, 2) to "105.00",
                LocalDate.of(2024, 1, 3) to "110.00",
            )),
        )

        assertEquals(3, result.size)
        assertBd("1000.00", result[0].value)
        assertBd("1000.00", result[0].invested)
        assertBd("1050.00", result[1].value)
        assertBd("1000.00", result[1].invested)
        assertBd("1100.00", result[2].value)
    }

    @Test
    fun `returns null value on dates without price but correct invested`() {
        // Price only for day 1 and day 3 — day 2 gets forward-filled from day 1, day 3 uses day 3 price
        val result = compute(
            transactions = listOf(buy(qty = "10", price = "100.00", date = LocalDate.of(2024, 1, 1))),
            from = LocalDate.of(2024, 1, 1), to = LocalDate.of(2024, 1, 3),
            priceHistory = mapOf(prices(eurListing.id,
                LocalDate.of(2024, 1, 1) to "100.00",
                LocalDate.of(2024, 1, 3) to "110.00",
            )),
        )

        assertEquals(3, result.size)
        assertEquals(true, result.all { it.value != null })
        assertBd("1000.00", result[1].value)
        assertBd("1100.00", result[2].value)
        // Invested is unaffected by price availability
        assertBd("1000.00", result[0].invested)
    }

    @Test
    fun `returns null value when no price history exists`() {
        val result = compute(
            transactions = listOf(buy(qty = "10", price = "100.00", date = LocalDate.of(2024, 1, 1))),
            from = LocalDate.of(2024, 1, 1), to = LocalDate.of(2024, 1, 2),
            // No price history seeded
        )

        assertEquals(2, result.size)
        assertNull(result[0].value)
        assertNull(result[1].value)
        // But invested is computable from transaction data
        assertBd("1000.00", result[0].invested)
    }

    @Test
    fun `returns null for dates before first transaction`() {
        val result = compute(
            transactions = listOf(buy(qty = "10", price = "100.00", date = LocalDate.of(2024, 1, 5))),
            from = LocalDate.of(2024, 1, 1), to = LocalDate.of(2024, 1, 6),
            priceHistory = mapOf(prices(eurListing.id, LocalDate.of(2024, 1, 5) to "100.00")),
        )

        assertEquals(6, result.size)
        // Days 1-4: no positions → null value, null invested
        assertNull(result[0].value)
        assertNull(result[0].invested)
        assertNull(result[3].value)
        assertNull(result[3].invested)
        // Day 5 (first buy): positions exist
        assertEquals(true, result[4].invested != null)
    }

    @Test
    fun `accounts for sell reducing invested amount`() {
        val result = compute(
            transactions = listOf(
                buy(qty = "10", price = "100.00", date = LocalDate.of(2024, 1, 1)),
                sell(qty = "4", price = "110.00", date = LocalDate.of(2024, 1, 3)),
            ),
            from = LocalDate.of(2024, 1, 1), to = LocalDate.of(2024, 1, 3),
            priceHistory = mapOf(prices(eurListing.id,
                LocalDate.of(2024, 1, 1) to "100.00",
                LocalDate.of(2024, 1, 3) to "110.00",
            )),
        )

        assertEquals(3, result.size)
        // After sell: invested = 1000 - (4 × 110) = 560
        assertBd("1000.00", result[0].invested)
        assertBd("560.00", result[2].invested)
        // Net qty after sell = 6; value = 6 × 110 = 660
        assertBd("660.00", result[2].value)
    }

    @Test
    fun `handles non-base-currency listing with FX rate`() {
        val buyDate = LocalDate.of(2024, 1, 1)
        val result = compute(
            transactions = listOf(buy(listingId = usdListing.id, qty = "10", price = "100.00", date = buyDate, fxRate = "1.10")),
            from = buyDate, to = buyDate,
            listings = listOf(eurListing, usdListing),
            priceHistory = mapOf(prices(usdListing.id, buyDate to "100.00")),
            fxRateHistory = mapOf(fxRates(usd, buyDate to "1.10")),
        )

        assertEquals(1, result.size)
        // value = 10 × 100 / 1.10 ≈ 909.09 (in base currency EUR)
        val expected = BigDecimal("1000.00").divide(BigDecimal("1.10"), 2, RoundingMode.HALF_UP)
        assertEquals(0, expected.compareTo(result[0].value!!.setScale(2, RoundingMode.HALF_UP)))
        // invested = 10 × 100 / 1.10 (uses the transaction's own recorded fx_rate)
        assertEquals(0, expected.compareTo(result[0].invested!!.setScale(2, RoundingMode.HALF_UP)))
    }

    @Test
    fun `sums only the priced listing when another listing has no price history`() {
        // Two holdings in the same portfolio: TST is priced, TST2 never gets any price data at
        // all (e.g. no provider mapping) — total value should reflect only TST's contribution,
        // not null out the whole day.
        val unpricedListing = Listing(id = 3L, assetId = 3L, exchange = null, ticker = "TST2", currency = eur)
        val date = LocalDate.of(2024, 1, 1)
        val result = compute(
            transactions = listOf(
                buy(listingId = eurListing.id, qty = "10", price = "100.00", date = date),
                buy(listingId = unpricedListing.id, qty = "5", price = "50.00", date = date),
            ),
            from = date, to = date,
            listings = listOf(eurListing, unpricedListing),
            priceHistory = mapOf(prices(eurListing.id, date to "100.00")),
            // No price history seeded for unpricedListing
        )

        assertEquals(1, result.size)
        // value = 10 × 100 (TST) only; the unpriced TST2 position is excluded, not nulling the total
        assertBd("1000.00", result[0].value)
        // invested is unaffected — it doesn't depend on price data
        assertBd("1250.00", result[0].invested)
    }

    @Test
    fun `returns null value when FX rate is missing for non-base-currency holding`() {
        val date = LocalDate.of(2024, 1, 1)
        val result = compute(
            transactions = listOf(buy(listingId = usdListing.id, qty = "10", price = "100.00", date = date, fxRate = "1.10")),
            from = date, to = date,
            listings = listOf(eurListing, usdListing),
            priceHistory = mapOf(prices(usdListing.id, date to "100.00")),
            // No FX rate history seeded → value must be null
        )

        assertEquals(1, result.size)
        assertNull(result[0].value)
    }
}
