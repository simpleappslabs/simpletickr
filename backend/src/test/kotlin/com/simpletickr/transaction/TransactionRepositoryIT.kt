package com.simpletickr.transaction

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionFilter
import com.simpletickr.transaction.persistence.TransactionRepository
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
@Import(TransactionRepository::class, PortfolioRepository::class, AssetRepository::class, ListingRepository::class, AccountRepository::class)
class TransactionRepositoryIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired private lateinit var repository: TransactionRepository
    @Autowired private lateinit var portfolioRepository: PortfolioRepository
    @Autowired private lateinit var assetRepository: AssetRepository
    @Autowired private lateinit var listingRepository: ListingRepository
    @Autowired private lateinit var accountRepository: AccountRepository

    private var portfolioId: Long = 0
    private var listingId: Long = 0
    private var assetId: Long = 0
    private var accountId: Long = 0

    @BeforeEach
    fun setup() {
        portfolioId = portfolioRepository.save("Test Portfolio", 1L).id
        val asset = assetRepository.save(null, "Test Asset", AssetType.STOCK)
        assetId = asset.id
        listingId = listingRepository.save(assetId, null, "TST_TXN", CurrencyCode("USD")).id
        accountId = accountRepository.save(Account(0L, 1L, "Test Account", null, AccountType.BROKERAGE, null, null, null)).id
    }

    private fun saveTransaction(
        type: TransactionType = TransactionType.BUY,
        quantity: BigDecimal = BigDecimal("10"),
        price: BigDecimal = BigDecimal("150.00"),
        date: LocalDate = LocalDate.of(2024, 1, 15),
        pId: Long = portfolioId,
        lId: Long = listingId,
        aId: Long = assetId,
    ) = repository.save(Transaction(0L, pId, lId, aId, type, quantity, price, date, null, accountId = accountId))

    // --- basic CRUD ---

    @Test
    fun `findAll returns empty list when no transactions exist`() {
        assertTrue(repository.findAll(TransactionFilter()).isEmpty())
    }

    @Test
    fun `count returns 0 when no transactions exist`() {
        assertEquals(0L, repository.count(TransactionFilter()))
        assertEquals(0L, repository.count(TransactionFilter(portfolioId = portfolioId)))
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
        val tx = repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), LocalDate.now(), BigDecimal("1.99"), accountId = accountId))
        assertEquals(0, BigDecimal("1.99").compareTo(tx.fees))
    }


    // --- portfolioId filter ---

    @Test
    fun `findAll with portfolioId filters by portfolio`() {
        val otherPortfolioId = portfolioRepository.save("Other Portfolio", 1L).id
        val otherAsset = assetRepository.save(null, "Test Asset 2", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "TST_TXN2", CurrencyCode("USD")).id

        saveTransaction()
        repository.save(Transaction(0L, otherPortfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("300"), LocalDate.now(), null, accountId = accountId))

        val results = repository.findAll(TransactionFilter(portfolioId = portfolioId))
        assertEquals(1, results.size)
        assertEquals(portfolioId, results[0].portfolioId)
    }

    @Test
    fun `count with portfolioId counts only that portfolio`() {
        val otherPortfolioId = portfolioRepository.save("Other Portfolio", 1L).id
        val otherAsset = assetRepository.save(null, "Test Asset 3", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "TST_CNT", CurrencyCode("USD")).id

        saveTransaction()
        saveTransaction()
        repository.save(Transaction(0L, otherPortfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.now(), null, accountId = accountId))

        assertEquals(2L, repository.count(TransactionFilter(portfolioId = portfolioId)))
        assertEquals(1L, repository.count(TransactionFilter(portfolioId = otherPortfolioId)))
        assertEquals(3L, repository.count(TransactionFilter()))
    }

    // --- pagination ---

    @Test
    fun `findAll paginates correctly`() {
        for (i in 1..5) saveTransaction()

        val page0 = repository.findAll(TransactionFilter(portfolioId = portfolioId), page = 0, size = 2)
        val page1 = repository.findAll(TransactionFilter(portfolioId = portfolioId), page = 1, size = 2)
        val page2 = repository.findAll(TransactionFilter(portfolioId = portfolioId), page = 2, size = 2)

        assertEquals(2, page0.size)
        assertEquals(2, page1.size)
        assertEquals(1, page2.size)
    }

    // --- type filter ---

    @Test
    fun `findAll filters by type`() {
        saveTransaction(type = TransactionType.BUY)
        saveTransaction(type = TransactionType.SELL)
        saveTransaction(type = TransactionType.SPLIT)

        val buys = repository.findAll(TransactionFilter(type = TransactionType.BUY))
        assertEquals(1, buys.size)
        assertEquals(TransactionType.BUY, buys[0].type)

        val sells = repository.findAll(TransactionFilter(type = TransactionType.SELL))
        assertEquals(1, sells.size)
        assertEquals(TransactionType.SELL, sells[0].type)
    }

    // --- listingId filter ---

    @Test
    fun `findAll filters by listingId`() {
        val otherAsset = assetRepository.save(null, "Other Asset", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "OTH", CurrencyCode("USD")).id

        saveTransaction()
        repository.save(Transaction(0L, portfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("10"), LocalDate.now(), null, accountId = accountId))

        val results = repository.findAll(TransactionFilter(listingId = listingId))
        assertEquals(1, results.size)
        assertEquals(listingId, results[0].listingId)
    }

    // --- date filters ---

    @Test
    fun `findAll filters by dateFrom`() {
        saveTransaction(date = LocalDate.of(2023, 1, 1))
        saveTransaction(date = LocalDate.of(2024, 6, 1))

        val results = repository.findAll(TransactionFilter(dateFrom = LocalDate.of(2024, 1, 1)))
        assertEquals(1, results.size)
        assertEquals(LocalDate.of(2024, 6, 1), results[0].date)
    }

    @Test
    fun `findAll filters by dateTo`() {
        saveTransaction(date = LocalDate.of(2023, 1, 1))
        saveTransaction(date = LocalDate.of(2024, 6, 1))

        val results = repository.findAll(TransactionFilter(dateTo = LocalDate.of(2023, 12, 31)))
        assertEquals(1, results.size)
        assertEquals(LocalDate.of(2023, 1, 1), results[0].date)
    }

    @Test
    fun `findAll includes boundaries for date range`() {
        saveTransaction(date = LocalDate.of(2024, 1, 1))
        saveTransaction(date = LocalDate.of(2024, 6, 15))
        saveTransaction(date = LocalDate.of(2024, 12, 31))

        val results = repository.findAll(TransactionFilter(
            dateFrom = LocalDate.of(2024, 1, 1),
            dateTo = LocalDate.of(2024, 12, 31),
        ))
        assertEquals(3, results.size)
    }

    // --- combination filters ---

    @Test
    fun `findAll filters by portfolioId and type`() {
        val otherPortfolioId = portfolioRepository.save("Other", 1L).id
        val otherAsset = assetRepository.save(null, "OA", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "OA_T", CurrencyCode("USD")).id

        saveTransaction(type = TransactionType.BUY)
        saveTransaction(type = TransactionType.SELL)
        repository.save(Transaction(0L, otherPortfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("10"), LocalDate.now(), null, accountId = accountId))

        val results = repository.findAll(TransactionFilter(portfolioId = portfolioId, type = TransactionType.BUY))
        assertEquals(1, results.size)
        assertEquals(portfolioId, results[0].portfolioId)
        assertEquals(TransactionType.BUY, results[0].type)
    }

    @Test
    fun `findAll filters by portfolioId and listingId`() {
        val otherAsset = assetRepository.save(null, "OtherAsset2", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "OA2", CurrencyCode("USD")).id

        saveTransaction()
        repository.save(Transaction(0L, portfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("10"), LocalDate.now(), null, accountId = accountId))

        val results = repository.findAll(TransactionFilter(portfolioId = portfolioId, listingId = listingId))
        assertEquals(1, results.size)
        assertEquals(listingId, results[0].listingId)
    }

    @Test
    fun `findAll filters by type and date range`() {
        saveTransaction(type = TransactionType.BUY, date = LocalDate.of(2023, 6, 1))
        saveTransaction(type = TransactionType.SELL, date = LocalDate.of(2024, 3, 1))
        saveTransaction(type = TransactionType.BUY, date = LocalDate.of(2024, 6, 1))

        val results = repository.findAll(TransactionFilter(
            type = TransactionType.BUY,
            dateFrom = LocalDate.of(2024, 1, 1),
        ))
        assertEquals(1, results.size)
        assertEquals(LocalDate.of(2024, 6, 1), results[0].date)
    }

    @Test
    fun `findAll with all filters applied`() {
        val otherAsset = assetRepository.save(null, "Other3", AssetType.STOCK)
        val otherListingId = listingRepository.save(otherAsset.id, null, "OA3", CurrencyCode("USD")).id

        saveTransaction(type = TransactionType.BUY, date = LocalDate.of(2024, 3, 1))
        saveTransaction(type = TransactionType.SELL, date = LocalDate.of(2024, 3, 1))
        saveTransaction(type = TransactionType.BUY, date = LocalDate.of(2023, 3, 1))
        repository.save(Transaction(0L, portfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("1"), BigDecimal("10"), LocalDate.of(2024, 3, 1), null, accountId = accountId))

        val results = repository.findAll(TransactionFilter(
            portfolioId = portfolioId,
            type = TransactionType.BUY,
            listingId = listingId,
            dateFrom = LocalDate.of(2024, 1, 1),
            dateTo = LocalDate.of(2024, 12, 31),
        ))
        assertEquals(1, results.size)
        assertEquals(portfolioId, results[0].portfolioId)
        assertEquals(TransactionType.BUY, results[0].type)
        assertEquals(listingId, results[0].listingId)
    }

    // --- count accuracy ---

    @Test
    fun `count uses same filter as findAll`() {
        saveTransaction(type = TransactionType.BUY)
        saveTransaction(type = TransactionType.SELL)

        assertEquals(2L, repository.count(TransactionFilter()))
        assertEquals(1L, repository.count(TransactionFilter(type = TransactionType.BUY)))
        assertEquals(1L, repository.count(TransactionFilter(type = TransactionType.SELL)))
        assertEquals(0L, repository.count(TransactionFilter(type = TransactionType.SPLIT)))
    }

    // --- other repository methods ---

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
            externalId = "bolero:abc123", accountId = accountId)
        repository.save(tx)

        assertTrue(repository.existsByExternalId(portfolioId, "bolero:abc123"))
    }

    @Test
    fun `existsByExternalId is scoped to portfolioId`() {
        val otherPortfolioId = portfolioRepository.save("Other", 1L).id
        val tx = Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY,
            BigDecimal("5"), BigDecimal("100"), LocalDate.of(2024, 1, 1), null,
            externalId = "bolero:xyz", accountId = accountId)
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
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.of(2022, 6, 1), null, accountId = accountId))
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.of(2020, 1, 15), null, accountId = accountId))
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("1"), BigDecimal("100"), LocalDate.of(2024, 3, 10), null, accountId = accountId))

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

        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), LocalDate.now(), null, accountId = accountId))
        repository.save(Transaction(0L, portfolioId, listingId, assetId, TransactionType.SELL, BigDecimal("5"), BigDecimal("110"), LocalDate.now(), null, accountId = accountId))
        repository.save(Transaction(0L, portfolioId, otherListingId, otherAsset.id, TransactionType.BUY, BigDecimal("3"), BigDecimal("50"), LocalDate.now(), null, accountId = accountId))

        val ids = repository.findDistinctListingIds(portfolioId)
        assertEquals(2, ids.size)
        assertTrue(ids.contains(listingId))
        assertTrue(ids.contains(otherListingId))
    }
}
