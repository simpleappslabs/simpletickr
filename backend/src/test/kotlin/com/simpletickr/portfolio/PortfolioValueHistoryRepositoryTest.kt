package com.simpletickr.portfolio

import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.portfolio.persistence.PortfolioValueHistoryRepository
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
import kotlin.test.assertNull

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(
    PortfolioValueHistoryRepository::class,
    PortfolioRepository::class,
    AssetRepository::class,
    ListingRepository::class,
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
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val eur = CurrencyCode("EUR")

    private var portfolioId: Long = 0
    private var listingId: Long = 0
    private var accountId: Long = 0

    @BeforeEach
    fun setup() {
        portfolioId = portfolioRepository.save("Test Portfolio", 1L).id
        val asset = assetRepository.save(null, "Test Asset", AssetType.STOCK)
        listingId = listingRepository.save(asset.id, null, "TST", eur).id
        accountId = jdbcTemplate.queryForObject(
            "INSERT INTO accounts (user_id, name, account_type) VALUES (1, 'Test', 'BROKERAGE') RETURNING id",
            Long::class.java,
        )!!
    }

    private fun insertTransaction(date: LocalDate, quantity: String = "10", price: String = "100") {
        jdbcTemplate.update(
            """INSERT INTO transactions (portfolio_id, listing_id, type, quantity, price, date, account_id)
               VALUES (?, ?, 'BUY', ?, ?, ?, ?)""",
            portfolioId, listingId, BigDecimal(quantity), BigDecimal(price), date, accountId,
        )
    }

    @Test
    fun `findOldestTransactionDate returns null when portfolio has no transactions`() {
        assertNull(repository.findOldestTransactionDate(portfolioId))
    }

    @Test
    fun `findOldestTransactionDate returns the earliest transaction date`() {
        insertTransaction(LocalDate.of(2024, 3, 1))
        insertTransaction(LocalDate.of(2024, 1, 15))
        insertTransaction(LocalDate.of(2024, 2, 10))

        assertEquals(LocalDate.of(2024, 1, 15), repository.findOldestTransactionDate(portfolioId))
    }
}
