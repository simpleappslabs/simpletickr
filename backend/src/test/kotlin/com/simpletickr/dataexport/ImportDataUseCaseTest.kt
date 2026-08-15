package com.simpletickr.dataexport

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.Asset
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.dataexport.model.AssetExport
import com.simpletickr.dataexport.model.ListingExport
import com.simpletickr.dataexport.model.PortfolioExport
import com.simpletickr.dataexport.model.PriceMappingExport
import com.simpletickr.dataexport.model.SettingsExport
import com.simpletickr.dataexport.model.SimpletickrExport
import com.simpletickr.dataexport.model.TransactionExport
import com.simpletickr.dataexport.model.TransferExport
import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transfer.TransferRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Wiring tests only: does ImportDataUseCase fetch the right existing state, hand it to
// ImportPlanner, and persist exactly what the plan says? Matching, ambiguity, and dedup
// semantics are ImportPlanner's own responsibility and are covered by ImportPlannerTest with
// plain data, no mocks.
class ImportDataUseCaseTest {

    private val assetRepository = mock<AssetRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val mappingRepository = mock<PriceProviderMappingRepository>()
    private val portfolioRepository = mock<PortfolioRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val transferRepository = mock<TransferRepository>()
    private val settingsRepository = mock<UserSettingsRepository>()
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    private val useCase = ImportDataUseCase(
        assetRepository, accountRepository, listingRepository, mappingRepository,
        portfolioRepository, transactionRepository, transferRepository, settingsRepository, objectMapper,
    )

    private val assetUuid = UUID.randomUUID()
    private val portfolioUuid = UUID.randomUUID()

    private val listingExport = ListingExport(
        id = 10L, exchange = "NYSE", ticker = "AAPL", currency = "USD",
        priceMappings = listOf(PriceMappingExport("YAHOO_FINANCE", "AAPL")),
    )
    private val txExport = TransactionExport(
        listingId = 10L, type = "BUY", quantity = BigDecimal("5"),
        price = BigDecimal("150.00"), date = LocalDate.of(2024, 1, 15),
        fees = BigDecimal("2.50"), fxRate = null, externalId = null,
    )
    private val assetExport = AssetExport(
        id = 1L, uuid = assetUuid, isin = "US0378331005", name = "Apple Inc.",
        type = "STOCK", listings = listOf(listingExport),
    )
    private val portfolioExport = PortfolioExport(
        id = 100L, uuid = portfolioUuid, name = "My Portfolio",
        transactions = listOf(txExport),
    )
    private val validExport = SimpletickrExport(
        schemaVersion = 1, exportedAt = Instant.now(),
        settings = SettingsExport("EUR"),
        assets = listOf(assetExport),
        portfolios = listOf(portfolioExport),
    )

    @BeforeEach
    fun setUp() {
        Mockito.reset(assetRepository, accountRepository, listingRepository, mappingRepository,
            portfolioRepository, transactionRepository, transferRepository, settingsRepository)
        whenever(assetRepository.findAll()).thenReturn(emptyList())
        whenever(accountRepository.findAllForUser(1L)).thenReturn(emptyList())
        whenever(portfolioRepository.findAllForUser(1L)).thenReturn(emptyList())
    }

    private fun bytes(export: SimpletickrExport) = objectMapper.writeValueAsBytes(export)

    @Test
    fun `analyze with invalid JSON returns error`() {
        val result = useCase.analyze("not json".toByteArray(), 1L)
        assertTrue(result.errors.isNotEmpty())
        assertEquals(0, result.assetsToCreate)
    }

    @Test
    fun `analyze fetches existing assets and portfolios and hands them to the plan`() {
        val existingAsset = Asset(1L, assetUuid, "US0378331005", "Apple Inc.", AssetType.STOCK,
            listings = listOf(Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))))
        val existingPortfolio = Portfolio(100L, portfolioUuid, "My Portfolio", 1L)
        whenever(assetRepository.findAll()).thenReturn(listOf(existingAsset))
        whenever(portfolioRepository.findAllForUser(1L)).thenReturn(listOf(existingPortfolio))
        whenever(transactionRepository.findAllForPortfolio(100L)).thenReturn(emptyList())
        whenever(transferRepository.findAllForPortfolio(100L)).thenReturn(emptyList())

        val result = useCase.analyze(bytes(validExport), 1L)

        assertEquals(0, result.assetsToCreate)
        assertEquals(1, result.assetsExisting)
        assertEquals(0, result.portfoliosToCreate)
        assertEquals(1, result.portfoliosExisting)
    }

    @Test
    fun `apply on fresh database creates every entity, including the implicit Default account`() {
        val newAsset = Asset(1L, assetUuid, null, "Apple Inc.", AssetType.STOCK, emptyList())
        val newListing = Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))
        val newPortfolio = Portfolio(100L, portfolioUuid, "My Portfolio", 1L)
        whenever(assetRepository.save(any(), any(), any(), any())).thenReturn(newAsset)
        whenever(listingRepository.save(any(), any(), any(), any())).thenReturn(newListing)
        whenever(portfolioRepository.save(any(), any(), any())).thenReturn(newPortfolio)
        whenever(mappingRepository.findByListingAndProvider(any(), any())).thenReturn(null)
        whenever(accountRepository.save(any())).thenAnswer { inv -> inv.getArgument<Account>(0).copy(id = 1L) }

        val result = useCase.apply(bytes(validExport), 1L)

        assertEquals(1, result.assetsCreated)
        assertEquals(1, result.listingsCreated)
        assertEquals(1, result.portfoliosCreated)
        assertEquals(1, result.accountsCreated)
        assertEquals(1, result.transactionsImported)
        verify(settingsRepository).update(1L, UserSettings(CurrencyCode("EUR")))
        verify(transactionRepository).save(any())
    }

    @Test
    fun `apply is idempotent when all entities already exist`() {
        val existingAsset = Asset(1L, assetUuid, null, "Apple Inc.", AssetType.STOCK,
            listings = listOf(Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))))
        val existingPortfolio = Portfolio(100L, portfolioUuid, "My Portfolio", 1L)
        val defaultAccount = Account(1L, 1L, "Default", null, AccountType.BROKERAGE, null, null, null)
        whenever(assetRepository.findAll()).thenReturn(listOf(existingAsset))
        whenever(portfolioRepository.findAllForUser(1L)).thenReturn(listOf(existingPortfolio))
        whenever(accountRepository.findAllForUser(1L)).thenReturn(listOf(defaultAccount))
        whenever(transactionRepository.findAllForPortfolio(100L)).thenReturn(listOf(
            com.simpletickr.transaction.model.Transaction(
                id = 1L, portfolioId = 100L, listingId = 10L, assetId = 1L,
                type = com.simpletickr.transaction.model.TransactionType.BUY,
                quantity = txExport.quantity, price = txExport.price, date = txExport.date,
                fees = txExport.fees, accountId = 1L,
            ),
        ))
        whenever(transferRepository.findAllForPortfolio(100L)).thenReturn(emptyList())

        val result = useCase.apply(bytes(validExport), 1L)

        assertEquals(0, result.assetsCreated)
        assertEquals(0, result.listingsCreated)
        assertEquals(0, result.portfoliosCreated)
        assertEquals(0, result.accountsCreated)
        assertEquals(0, result.transactionsImported)
        verify(transactionRepository, never()).save(any())
        verify(accountRepository, never()).save(any())
    }

    @Test
    fun `apply with validation errors throws exception`() {
        val export = validExport.copy(schemaVersion = 99)
        try {
            useCase.apply(bytes(export), 1L)
            assertTrue(false, "Expected exception")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("version") == true || e.message?.contains("400") == true)
        }
    }

    @Test
    fun `apply on fresh database creates a transfer and resolves both accounts by name`() {
        val newAsset = Asset(1L, assetUuid, null, "Apple Inc.", AssetType.STOCK, emptyList())
        val newListing = Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))
        val newPortfolio = Portfolio(100L, portfolioUuid, "My Portfolio", 1L)
        whenever(assetRepository.save(any(), any(), any(), any())).thenReturn(newAsset)
        whenever(listingRepository.save(any(), any(), any(), any())).thenReturn(newListing)
        whenever(portfolioRepository.save(any(), any(), any())).thenReturn(newPortfolio)
        whenever(mappingRepository.findByListingAndProvider(any(), any())).thenReturn(null)
        var nextAccountId = 1L
        whenever(accountRepository.save(any())).thenAnswer { inv -> inv.getArgument<Account>(0).copy(id = nextAccountId++) }

        val transferExport = TransferExport(
            listingId = 10L, quantity = BigDecimal("3"), assetFeeQuantity = null,
            date = LocalDate.of(2024, 2, 1),
            sourceAccountName = "Exchange", destinationAccountName = "Cold Wallet",
        )
        val export = validExport.copy(portfolios = listOf(portfolioExport.copy(transactions = emptyList(), transfers = listOf(transferExport))))

        val result = useCase.apply(bytes(export), 1L)

        assertEquals(1, result.transfersImported)
        assertEquals(2, result.accountsCreated) // Exchange, Cold Wallet — no transaction to imply a Default one
        verify(transferRepository).create(any())
    }
}
