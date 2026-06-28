package com.simpletickr.dataexport

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.persistence.TransactionRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
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

class ImportDataUseCaseTest {

    private val assetRepository = mock<AssetRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val mappingRepository = mock<PriceProviderMappingRepository>()
    private val portfolioRepository = mock<PortfolioRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val settingsRepository = mock<UserSettingsRepository>()
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    private val useCase = ImportDataUseCase(
        assetRepository, listingRepository, mappingRepository,
        portfolioRepository, transactionRepository, settingsRepository, objectMapper,
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
        Mockito.reset(assetRepository, listingRepository, mappingRepository,
            portfolioRepository, transactionRepository, settingsRepository)
        whenever(assetRepository.findAll()).thenReturn(emptyList())
        whenever(portfolioRepository.findAll()).thenReturn(emptyList())
        whenever(transactionRepository.existsIdentical(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(false)
    }

    private fun bytes(export: SimpletickrExport) = objectMapper.writeValueAsBytes(export)

    @Test
    fun `analyze with invalid JSON returns error`() {
        val result = useCase.analyze("not json".toByteArray())
        assertTrue(result.errors.isNotEmpty())
        assertEquals(0, result.assetsToCreate)
    }

    @Test
    fun `analyze with unsupported schema version returns error`() {
        val export = validExport.copy(schemaVersion = 99)
        val result = useCase.analyze(bytes(export))
        assertTrue(result.errors.any { it.contains("version") })
    }

    @Test
    fun `analyze with transaction referencing missing listing returns error`() {
        val badTx = txExport.copy(listingId = 999L)
        val export = validExport.copy(
            portfolios = listOf(portfolioExport.copy(transactions = listOf(badTx)))
        )
        val result = useCase.analyze(bytes(export))
        assertTrue(result.errors.any { it.contains("999") })
    }

    @Test
    fun `analyze with duplicate asset ID in export returns error`() {
        val export = validExport.copy(assets = listOf(assetExport, assetExport))
        val result = useCase.analyze(bytes(export))
        assertTrue(result.errors.any { it.contains("Duplicate asset") })
    }

    @Test
    fun `analyze fresh database counts all entities as to-create`() {
        val result = useCase.analyze(bytes(validExport))
        assertEquals(emptyList(), result.errors)
        assertEquals(1, result.assetsToCreate)
        assertEquals(0, result.assetsExisting)
        assertEquals(1, result.listingsToCreate)
        assertEquals(0, result.listingsExisting)
        assertEquals(1, result.portfoliosToCreate)
        assertEquals(0, result.portfoliosExisting)
        assertEquals(1, result.transactionsToImport)
        assertEquals(0, result.transactionsSkipped)
    }

    @Test
    fun `analyze existing asset matched by UUID shows it as existing`() {
        val existingAsset = Asset(1L, assetUuid, "US0378331005", "Apple Inc.", AssetType.STOCK,
            listings = listOf(Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))))
        whenever(assetRepository.findAll()).thenReturn(listOf(existingAsset))

        val result = useCase.analyze(bytes(validExport))
        assertEquals(0, result.assetsToCreate)
        assertEquals(1, result.assetsExisting)
        assertEquals(0, result.listingsToCreate)
        assertEquals(1, result.listingsExisting)
    }

    @Test
    fun `analyze existing portfolio matched by UUID shows it as existing with transaction dedup`() {
        val existingAsset = Asset(1L, assetUuid, null, "Apple Inc.", AssetType.STOCK,
            listings = listOf(Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))))
        val existingPortfolio = Portfolio(100L, portfolioUuid, "My Portfolio")
        whenever(assetRepository.findAll()).thenReturn(listOf(existingAsset))
        whenever(portfolioRepository.findAll()).thenReturn(listOf(existingPortfolio))
        whenever(transactionRepository.existsIdentical(
            eq(100L), eq(10L), any(), any(), any(), any(), anyOrNull(), anyOrNull()
        )).thenReturn(true)

        val result = useCase.analyze(bytes(validExport))
        assertEquals(0, result.transactionsToImport)
        assertEquals(1, result.transactionsSkipped)
    }

    @Test
    fun `apply on fresh database creates all entities`() {
        val newAsset = Asset(1L, assetUuid, null, "Apple Inc.", AssetType.STOCK, emptyList())
        val newListing = Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))
        val newPortfolio = Portfolio(100L, portfolioUuid, "My Portfolio")
        whenever(assetRepository.save(any(), any(), any())).thenReturn(newAsset)
        whenever(listingRepository.save(any(), any(), any(), any())).thenReturn(newListing)
        whenever(listingRepository.findByAssetId(1L)).thenReturn(emptyList())
        whenever(portfolioRepository.save(any())).thenReturn(newPortfolio)
        whenever(mappingRepository.findByListingAndProvider(any(), any())).thenReturn(null)

        val result = useCase.apply(bytes(validExport))

        assertEquals(1, result.assetsCreated)
        assertEquals(1, result.listingsCreated)
        assertEquals(1, result.portfoliosCreated)
        assertEquals(1, result.transactionsImported)
        verify(settingsRepository).update(UserSettings(CurrencyCode("EUR")))
        verify(transactionRepository).save(any())
    }

    @Test
    fun `apply is idempotent when all entities already exist`() {
        val existingAsset = Asset(1L, assetUuid, null, "Apple Inc.", AssetType.STOCK,
            listings = listOf(Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))))
        val existingPortfolio = Portfolio(100L, portfolioUuid, "My Portfolio")
        whenever(assetRepository.findAll()).thenReturn(listOf(existingAsset))
        whenever(portfolioRepository.findAll()).thenReturn(listOf(existingPortfolio))
        whenever(listingRepository.findByAssetId(1L))
            .thenReturn(listOf(Listing(10L, 1L, "NYSE", "AAPL", CurrencyCode("USD"))))
        whenever(mappingRepository.findByListingAndProvider(eq(10L), any())).thenReturn(null)
        whenever(transactionRepository.existsIdentical(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(true)

        val result = useCase.apply(bytes(validExport))

        assertEquals(0, result.assetsCreated)
        assertEquals(0, result.listingsCreated)
        assertEquals(0, result.portfoliosCreated)
        assertEquals(0, result.transactionsImported)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `apply with validation errors throws exception`() {
        val export = validExport.copy(schemaVersion = 99)
        try {
            useCase.apply(bytes(export))
            assertTrue(false, "Expected exception")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("version") == true || e.message?.contains("400") == true)
        }
    }
}
