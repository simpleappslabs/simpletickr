package com.simpletickr.portfolio

import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.portfolio.persistence.PortfolioValueHistoryRepository
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.price.model.PricePoint
import com.simpletickr.shared.CurrencyCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(
    PortfolioValueHistoryRepository::class,
    PortfolioRepository::class,
    AssetRepository::class,
    ListingRepository::class,
    AssetPriceHistoryRepository::class,
)
class PortfolioValueHistoryRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired private lateinit var repository: PortfolioValueHistoryRepository
    @Autowired private lateinit var portfolioRepository: PortfolioRepository
    @Autowired private lateinit var assetRepository: AssetRepository
    @Autowired private lateinit var listingRepository: ListingRepository
    @Autowired private lateinit var priceRepository: AssetPriceHistoryRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val eur = CurrencyCode("EUR")
    private val usd = CurrencyCode("USD")

    private var portfolioId: Long = 0
    private var eurListingId: Long = 0
    private var accountId: Long = 0

    @BeforeEach
    fun setup() {
        portfolioId = portfolioRepository.save("Test Portfolio").id
        val asset = assetRepository.save(null, "Test Asset", AssetType.STOCK)
        eurListingId = listingRepository.save(asset.id, null, "TST", eur).id
        accountId = jdbcTemplate.queryForObject(
            "INSERT INTO accounts (name, account_type) VALUES ('Test', 'BROKERAGE') RETURNING id",
            Long::class.java,
        )!!
    }

    private fun insertTransaction(
        listingId: Long = eurListingId,
        type: String = "BUY",
        quantity: BigDecimal,
        price: BigDecimal,
        date: LocalDate,
        fxRate: BigDecimal? = null,
    ) {
        jdbcTemplate.update(
            """INSERT INTO transactions (portfolio_id, listing_id, type, quantity, price, date, fx_rate, account_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            portfolioId, listingId, type, quantity, price, date, fxRate, accountId,
        )
    }

    private fun insertFxRate(baseCurrency: String, quoteCurrency: String, date: LocalDate, rate: BigDecimal) {
        jdbcTemplate.update(
            """INSERT INTO fx_rates (base_currency, quote_currency, date, rate)
               VALUES (?, ?, ?, ?)
               ON CONFLICT (base_currency, quote_currency, date) DO UPDATE SET rate = EXCLUDED.rate""",
            baseCurrency, quoteCurrency, date, rate,
        )
    }

    @Test
    fun `findOldestTransactionDate returns null when portfolio has no transactions`() {
        assertNull(repository.findOldestTransactionDate(portfolioId))
    }

    @Test
    fun `findOldestTransactionDate returns the earliest transaction date`() {
        insertTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"), date = LocalDate.of(2024, 3, 1))
        insertTransaction(quantity = BigDecimal("5"), price = BigDecimal("110"), date = LocalDate.of(2024, 1, 15))
        insertTransaction(quantity = BigDecimal("3"), price = BigDecimal("95"), date = LocalDate.of(2024, 2, 10))

        assertEquals(LocalDate.of(2024, 1, 15), repository.findOldestTransactionDate(portfolioId))
    }

    @Test
    fun `findValueHistory returns null points when no transactions`() {
        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3))
        // Repository always returns one row per date; nulls signal no data (service short-circuits to empty list upstream)
        assertEquals(3, result.size)
        assertTrue(result.all { it.value == null })
        assertTrue(result.all { it.invested == null })
    }

    @Test
    fun `findValueHistory returns correct value and invested for base-currency holding`() {
        val buyDate = LocalDate.of(2024, 1, 1)
        insertTransaction(quantity = BigDecimal("10"), price = BigDecimal("100.00"), date = buyDate)
        priceRepository.upsert(eurListingId, listOf(
            PricePoint(LocalDate.of(2024, 1, 1), BigDecimal("100.00")),
            PricePoint(LocalDate.of(2024, 1, 2), BigDecimal("105.00")),
            PricePoint(LocalDate.of(2024, 1, 3), BigDecimal("110.00")),
        ))

        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3))

        assertEquals(3, result.size)
        // value = 10 × price; invested = 10 × 100 = 1000 (cumulative, same each day)
        assertEquals(0, BigDecimal("1000.00").compareTo(result[0].value))
        assertEquals(0, BigDecimal("1000.00").compareTo(result[0].invested))
        assertEquals(0, BigDecimal("1050.00").compareTo(result[1].value))
        assertEquals(0, BigDecimal("1000.00").compareTo(result[1].invested))
        assertEquals(0, BigDecimal("1100.00").compareTo(result[2].value))
    }

    @Test
    fun `findValueHistory returns null value on dates without price but correct invested`() {
        insertTransaction(quantity = BigDecimal("10"), price = BigDecimal("100.00"), date = LocalDate.of(2024, 1, 1))
        // Price only for day 1 and day 3 — day 2 gets forward-filled from day 1, day 3 uses day 3 price
        priceRepository.upsert(eurListingId, listOf(
            PricePoint(LocalDate.of(2024, 1, 1), BigDecimal("100.00")),
            PricePoint(LocalDate.of(2024, 1, 3), BigDecimal("110.00")),
        ))

        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3))

        assertEquals(3, result.size)
        // Day 1: price 100 → value = 1000; day 2: forward-filled price 100 → value = 1000; day 3: price 110 → value = 1100
        assertNotNull(result[0].value)
        assertNotNull(result[1].value)
        assertNotNull(result[2].value)
        assertEquals(0, BigDecimal("1000.00").compareTo(result[1].value))
        assertEquals(0, BigDecimal("1100.00").compareTo(result[2].value))
        // Invested is unaffected by price availability
        assertEquals(0, BigDecimal("1000.00").compareTo(result[0].invested))
    }

    @Test
    fun `findValueHistory returns null value when no price history exists`() {
        insertTransaction(quantity = BigDecimal("10"), price = BigDecimal("100.00"), date = LocalDate.of(2024, 1, 1))
        // No price history seeded

        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2))

        assertEquals(2, result.size)
        assertNull(result[0].value)
        assertNull(result[1].value)
        // But invested is computable from transaction data
        assertNotNull(result[0].invested)
        assertEquals(0, BigDecimal("1000.00").compareTo(result[0].invested))
    }

    @Test
    fun `findValueHistory returns null for dates before first transaction`() {
        insertTransaction(quantity = BigDecimal("10"), price = BigDecimal("100.00"), date = LocalDate.of(2024, 1, 5))
        priceRepository.upsert(eurListingId, listOf(PricePoint(LocalDate.of(2024, 1, 5), BigDecimal("100.00"))))

        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 6))

        assertEquals(6, result.size)
        // Days 1–4: no positions → null value, null invested
        assertNull(result[0].value)
        assertNull(result[0].invested)
        assertNull(result[3].value)
        assertNull(result[3].invested)
        // Day 5 (first buy): positions exist
        assertNotNull(result[4].invested)
    }

    @Test
    fun `findValueHistory accounts for sell reducing invested amount`() {
        insertTransaction(type = "BUY", quantity = BigDecimal("10"), price = BigDecimal("100.00"), date = LocalDate.of(2024, 1, 1))
        insertTransaction(type = "SELL", quantity = BigDecimal("4"), price = BigDecimal("110.00"), date = LocalDate.of(2024, 1, 3))
        priceRepository.upsert(eurListingId, listOf(
            PricePoint(LocalDate.of(2024, 1, 1), BigDecimal("100.00")),
            PricePoint(LocalDate.of(2024, 1, 3), BigDecimal("110.00")),
        ))

        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3))

        assertEquals(3, result.size)
        // After sell: invested = 1000 - (4 × 110) = 560
        assertEquals(0, BigDecimal("1000.00").compareTo(result[0].invested))
        assertEquals(0, BigDecimal("560.00").compareTo(result[2].invested))
        // Net qty after sell = 6; value = 6 × 110 = 660
        assertEquals(0, BigDecimal("660.00").compareTo(result[2].value))
    }

    @Test
    fun `findValueHistory handles non-base-currency listing with FX rate`() {
        val asset = assetRepository.save(null, "US Asset", AssetType.STOCK)
        val usdListingId = listingRepository.save(asset.id, null, "USD_STK", usd).id

        val buyDate = LocalDate.of(2024, 1, 1)
        insertTransaction(
            listingId = usdListingId,
            quantity = BigDecimal("10"),
            price = BigDecimal("100.00"),
            date = buyDate,
            fxRate = BigDecimal("1.10"),
        )
        priceRepository.upsert(usdListingId, listOf(PricePoint(LocalDate.of(2024, 1, 1), BigDecimal("100.00"))))
        // 1 EUR = 1.10 USD → value_eur = 1000 / 1.10 ≈ 909.09
        insertFxRate("EUR", "USD", LocalDate.of(2024, 1, 1), BigDecimal("1.10"))

        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1))

        assertEquals(1, result.size)
        assertNotNull(result[0].value)
        // value = 10 × 100 / 1.10 ≈ 909.09 (in base currency EUR)
        assertEquals(
            0,
            BigDecimal("909.09").compareTo(result[0].value!!.setScale(2, java.math.RoundingMode.HALF_UP))
        )
        // invested = 10 × 100 / 1.10 (uses transaction's own fx_rate)
        assertNotNull(result[0].invested)
        assertEquals(
            0,
            BigDecimal("909.09").compareTo(result[0].invested!!.setScale(2, java.math.RoundingMode.HALF_UP))
        )
    }

    @Test
    fun `findValueHistory returns null value when FX rate is missing for non-base-currency holding`() {
        val asset = assetRepository.save(null, "US Asset", AssetType.STOCK)
        val usdListingId = listingRepository.save(asset.id, null, "USD_STK2", usd).id

        insertTransaction(
            listingId = usdListingId,
            quantity = BigDecimal("10"),
            price = BigDecimal("100.00"),
            date = LocalDate.of(2024, 1, 1),
            fxRate = BigDecimal("1.10"),
        )
        priceRepository.upsert(usdListingId, listOf(PricePoint(LocalDate.of(2024, 1, 1), BigDecimal("100.00"))))
        // No FX rate seeded → value must be null

        val result = repository.findValueHistory(portfolioId, eur, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1))

        assertEquals(1, result.size)
        assertNull(result[0].value)
    }
}
