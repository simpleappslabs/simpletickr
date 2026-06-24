package com.simpletickr.transaction

import com.simpletickr.asset.AssetRepository
import com.simpletickr.asset.AssetType
import com.simpletickr.asset.ListingRepository
import com.simpletickr.shared.CurrencyCode
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(TransactionRepository::class, PortfolioRepository::class, AssetRepository::class, ListingRepository::class)
class TransactionRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired private lateinit var repository: TransactionRepository
    @Autowired private lateinit var portfolioRepository: PortfolioRepository
    @Autowired private lateinit var assetRepository: AssetRepository
    @Autowired private lateinit var listingRepository: ListingRepository

    private var portfolioId: Long = 0
    private var listingId: Long = 0
    private var assetId: Long = 0

    @BeforeEach
    fun setup() {
        portfolioId = portfolioRepository.save("Test Portfolio").id
        val asset = assetRepository.save(null, "Test Asset", AssetType.STOCK)
        assetId = asset.id
        listingId = listingRepository.save(assetId, null, "TST_TXN", CurrencyCode("USD")).id
    }

    private fun saveTransaction(
        type: TransactionType = TransactionType.BUY,
        quantity: BigDecimal = BigDecimal("10"),
        price: BigDecimal = BigDecimal("150.00"),
    ) = repository.save(Transaction(0L, portfolioId, listingId, assetId, type, quantity, price, LocalDate.of(2024, 1, 15), null))

    @Test
    fun `findAll returns empty list when no transactions exist`() {
        assertTrue(repository.findAll(null).isEmpty())
    }

    @Test
    fun `count returns 0 when no transactions exist`() {
        assertEquals(0L, repository.count(null))
        assertEquals(0L, repository.count(portfolioId))
    }

    @Test
    fun `save creates a transaction and returns it with a generated id`() {
        val tx = saveTransaction()
        assertTrue(tx.id > 0)
        assertEquals(portfolioId, tx.portfolioId)
        assertEquals(listingId, tx.listingId)
        assertEquals(assetId, tx.assetId)
        assertEquals(TransactionType.BUY, tx.type)
        assertEquals(0, BigDecimal("10").compareTo(tx.quantity))
        assertEquals(0, BigDecimal("150.00").compareTo(tx.price))
        assertEquals(LocalDate.of(2024, 1, 15), tx.date)
        assertNull(tx.fees)
    }

    @Test
    fun `save stores fees when provided`() {
        val tx = repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), LocalDate.now(), BigDecimal("1.99")))
        assertEquals(0, BigDecimal("1.99").compareTo(tx.fees))
    }

    @Test
    fun `findAll with portfolioId filters by portfolio`() {
        val otherPortfolioId = portfolioRepository.save("Other Portfolio").id
        val otherAsset = assetRepository.save(null, "Test Asset 2", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "TST_TXN2", CurrencyCode("USD")).id

        saveTransaction()
        repository.save(Transaction(0L, otherPortfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("300"), LocalDate.now(), null))

        val results = repository.findAll(portfolioId)
        assertEquals(1, results.size)
        assertEquals(portfolioId, results[0].portfolioId)
    }

    @Test
    fun `count with portfolioId counts only that portfolio`() {
        val otherPortfolioId = portfolioRepository.save("Other Portfolio").id
        val otherAsset = assetRepository.save(null, "Test Asset 3", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "TST_CNT", CurrencyCode("USD")).id

        saveTransaction()
        saveTransaction()
        repository.save(Transaction(0L, otherPortfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.now(), null))

        assertEquals(2L, repository.count(portfolioId))
        assertEquals(1L, repository.count(otherPortfolioId))
        assertEquals(3L, repository.count(null))
    }

    @Test
    fun `findAll paginates correctly`() {
        for (i in 1..5) saveTransaction()

        val page0 = repository.findAll(portfolioId, page = 0, size = 2)
        val page1 = repository.findAll(portfolioId, page = 1, size = 2)
        val page2 = repository.findAll(portfolioId, page = 2, size = 2)

        assertEquals(2, page0.size)
        assertEquals(2, page1.size)
        assertEquals(1, page2.size)
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

    @Test
    fun `existsByExternalId returns false when not present`() {
        assertFalse(repository.existsByExternalId(portfolioId, "bolero:somehash"))
    }

    @Test
    fun `existsByExternalId returns true after saving with that externalId`() {
        val tx = Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY,
            BigDecimal("5"), BigDecimal("100"), LocalDate.of(2024, 1, 1), null,
            externalId = "bolero:abc123")
        repository.save(tx)

        assertTrue(repository.existsByExternalId(portfolioId, "bolero:abc123"))
    }

    @Test
    fun `existsByExternalId is scoped to portfolioId`() {
        val otherPortfolioId = portfolioRepository.save("Other").id
        val tx = Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY,
            BigDecimal("5"), BigDecimal("100"), LocalDate.of(2024, 1, 1), null,
            externalId = "bolero:xyz")
        repository.save(tx)

        assertFalse(repository.existsByExternalId(otherPortfolioId, "bolero:xyz"))
        assertTrue(repository.existsByExternalId(portfolioId, "bolero:xyz"))
    }

    @Test
    fun `findOldestTransactionDate returns null when no transactions exist`() {
        assertNull(repository.findOldestTransactionDate(portfolioId))
    }

    @Test
    fun `findOldestTransactionDate returns the earliest transaction date`() {
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.of(2022, 6, 1), null))
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.of(2020, 1, 15), null))
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.of(2024, 3, 10), null))

        assertEquals(LocalDate.of(2020, 1, 15), repository.findOldestTransactionDate(portfolioId))
    }

    @Test
    fun `findDistinctListingIds returns empty list when no transactions exist`() {
        assertTrue(repository.findDistinctListingIds(portfolioId).isEmpty())
    }

    @Test
    fun `findDistinctListingIds returns deduplicated listing ids including sold positions`() {
        val otherAsset = assetRepository.save(null, "Other Asset", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "OTHER", CurrencyCode("EUR")).id

        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), LocalDate.now(), null))
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.SELL, BigDecimal("5"), BigDecimal("110"), LocalDate.now(), null))
        repository.save(Transaction(0L, portfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("3"), BigDecimal("50"), LocalDate.now(), null))

        val ids = repository.findDistinctListingIds(portfolioId)
        assertEquals(2, ids.size)
        assertTrue(ids.contains(listingId))
        assertTrue(ids.contains(otherListingId))
    }
}
