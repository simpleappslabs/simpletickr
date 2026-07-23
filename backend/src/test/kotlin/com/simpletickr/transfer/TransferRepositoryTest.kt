package com.simpletickr.transfer

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.shared.CurrencyCode
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(TransferRepository::class, PortfolioRepository::class, AssetRepository::class, ListingRepository::class, AccountRepository::class)
class TransferRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired private lateinit var repository: TransferRepository
    @Autowired private lateinit var portfolioRepository: PortfolioRepository
    @Autowired private lateinit var assetRepository: AssetRepository
    @Autowired private lateinit var listingRepository: ListingRepository
    @Autowired private lateinit var accountRepository: AccountRepository

    private var portfolioId: Long = 0
    private var listingId: Long = 0
    private var assetId: Long = 0
    private var sourceAccountId: Long = 0
    private var destinationAccountId: Long = 0

    @BeforeEach
    fun setup() {
        portfolioId = portfolioRepository.save("Test Portfolio").id
        val asset = assetRepository.save(null, "Test Asset", AssetType.CRYPTO)
        assetId = asset.id
        listingId = listingRepository.save(assetId, null, "TST_XFER", CurrencyCode("USD")).id
        sourceAccountId = accountRepository.save(Account(0L, "Exchange", null, AccountType.CRYPTO, null, null, null)).id
        destinationAccountId = accountRepository.save(Account(0L, "Cold Wallet", null, AccountType.CRYPTO, null, null, null)).id
    }

    private fun saveTransfer(
        quantity: BigDecimal = BigDecimal("1.0"),
        assetFeeQuantity: BigDecimal? = null,
        date: LocalDate = LocalDate.of(2024, 6, 1),
    ) = repository.create(Transfer(
        id = 0L, portfolioId = portfolioId, listingId = listingId, assetId = assetId,
        quantity = quantity, assetFeeQuantity = assetFeeQuantity, date = date,
        sourceAccountId = sourceAccountId, destinationAccountId = destinationAccountId,
    ))

    @Test
    fun `findAllForPortfolio returns empty list when no transfers exist`() {
        assertTrue(repository.findAllForPortfolio(portfolioId).isEmpty())
    }

    @Test
    fun `create and findById round-trip a transfer without a fee`() {
        val saved = saveTransfer()
        assertTrue(saved.id > 0)

        val found = repository.findById(saved.id)!!
        assertEquals(portfolioId, found.portfolioId)
        assertEquals(listingId, found.listingId)
        assertEquals(assetId, found.assetId)
        assertEquals(0, BigDecimal("1.0").compareTo(found.quantity))
        assertNull(found.assetFeeQuantity)
        assertEquals(sourceAccountId, found.sourceAccountId)
        assertEquals(destinationAccountId, found.destinationAccountId)
    }

    @Test
    fun `create and findById round-trip a transfer with a fee`() {
        val saved = saveTransfer(assetFeeQuantity = BigDecimal("0.005"))
        val found = repository.findById(saved.id)!!
        assertEquals(0, BigDecimal("0.005").compareTo(found.assetFeeQuantity))
    }

    @Test
    fun `findById returns null when not found`() {
        assertEquals(null, repository.findById(999999L))
    }

    @Test
    fun `findAllForPortfolio orders by date then id`() {
        val later = saveTransfer(date = LocalDate.of(2024, 6, 1))
        val earlier = saveTransfer(date = LocalDate.of(2024, 1, 1))

        val transfers = repository.findAllForPortfolio(portfolioId)

        assertEquals(2, transfers.size)
        assertEquals(earlier.id, transfers[0].id)
        assertEquals(later.id, transfers[1].id)
    }

    @Test
    fun `delete removes the transfer`() {
        val saved = saveTransfer()
        repository.delete(saved.id)
        assertEquals(null, repository.findById(saved.id))
    }

    @Test
    fun `existsForAccountInPortfolio is true for both source and destination accounts`() {
        saveTransfer()
        assertTrue(repository.existsForAccountInPortfolio(sourceAccountId, portfolioId))
        assertTrue(repository.existsForAccountInPortfolio(destinationAccountId, portfolioId))
    }

    @Test
    fun `existsForAccountInPortfolio is false for an unrelated account`() {
        saveTransfer()
        val unrelatedAccountId = accountRepository.save(Account(0L, "Unrelated", null, AccountType.CRYPTO, null, null, null)).id
        assertEquals(false, repository.existsForAccountInPortfolio(unrelatedAccountId, portfolioId))
    }

    @Test
    fun `existsIdentical is true for a matching transfer and false when any field differs`() {
        saveTransfer(quantity = BigDecimal("2.0"), assetFeeQuantity = BigDecimal("0.01"), date = LocalDate.of(2024, 3, 1))

        assertTrue(repository.existsIdentical(
            portfolioId, listingId, LocalDate.of(2024, 3, 1),
            BigDecimal("2.0"), BigDecimal("0.01"), sourceAccountId, destinationAccountId,
        ))
        assertEquals(false, repository.existsIdentical(
            portfolioId, listingId, LocalDate.of(2024, 3, 1),
            BigDecimal("3.0"), BigDecimal("0.01"), sourceAccountId, destinationAccountId,
        ))
        assertEquals(false, repository.existsIdentical(
            portfolioId, listingId, LocalDate.of(2024, 3, 2),
            BigDecimal("2.0"), BigDecimal("0.01"), sourceAccountId, destinationAccountId,
        ))
    }
}
