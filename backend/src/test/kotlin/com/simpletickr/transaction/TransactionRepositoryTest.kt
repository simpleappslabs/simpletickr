package com.simpletickr.transaction

import com.simpletickr.asset.AssetRepository
import com.simpletickr.asset.AssetType
import com.simpletickr.portfolio.PortfolioRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
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
@Import(TransactionRepository::class, PortfolioRepository::class, AssetRepository::class)
class TransactionRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: TransactionRepository

    @Autowired
    private lateinit var portfolioRepository: PortfolioRepository

    @Autowired
    private lateinit var assetRepository: AssetRepository

    private var portfolioId: Long = 0
    private var assetId: Long = 0

    @BeforeEach
    fun setup() {
        portfolioId = portfolioRepository.save("Test Portfolio").id
        assetId = assetRepository.save("TST_TXN", "Test Asset", AssetType.STOCK, "USD", null).id
    }

    private fun saveTransaction(
        type: TransactionType = TransactionType.BUY,
        quantity: BigDecimal = BigDecimal("10"),
        price: BigDecimal = BigDecimal("150.00"),
    ) = repository.save(portfolioId, assetId, type, quantity, price, LocalDate.of(2024, 1, 15), null)

    @Test
    fun `findAll returns empty list when no transactions exist`() {
        assertTrue(repository.findAll(null).isEmpty())
    }

    @Test
    fun `save creates a transaction and returns it with a generated id`() {
        val tx = saveTransaction()
        assertTrue(tx.id > 0)
        assertEquals(portfolioId, tx.portfolioId)
        assertEquals(assetId, tx.assetId)
        assertEquals(TransactionType.BUY, tx.type)
        assertEquals(0, BigDecimal("10").compareTo(tx.quantity))
        assertEquals(0, BigDecimal("150.00").compareTo(tx.price))
        assertEquals(LocalDate.of(2024, 1, 15), tx.date)
        assertNull(tx.fees)
    }

    @Test
    fun `save stores fees when provided`() {
        val tx = repository.save(portfolioId, assetId, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), LocalDate.now(), BigDecimal("1.99"))
        assertEquals(0, BigDecimal("1.99").compareTo(tx.fees))
    }

    @Test
    fun `findAll with portfolioId filters by portfolio`() {
        val otherPortfolioId = portfolioRepository.save("Other Portfolio").id
        val otherAssetId = assetRepository.save("TST_TXN2", "Test Asset 2", AssetType.STOCK, "USD", null).id

        saveTransaction()
        repository.save(otherPortfolioId, otherAssetId, TransactionType.BUY, BigDecimal("1"), BigDecimal("300"), LocalDate.now(), null)

        val results = repository.findAll(portfolioId)
        assertEquals(1, results.size)
        assertEquals(portfolioId, results[0].portfolioId)
    }

    @Test
    fun `findById returns the transaction when it exists`() {
        val saved = saveTransaction()
        val found = repository.findById(saved.id)
        assertNotNull(found)
        assertEquals(saved.id, found.id)
    }

    @Test
    fun `findById returns null when transaction does not exist`() {
        assertNull(repository.findById(-1L))
    }

    @Test
    fun `delete removes the transaction`() {
        val saved = saveTransaction()
        repository.delete(saved.id)
        assertNull(repository.findById(saved.id))
    }
}