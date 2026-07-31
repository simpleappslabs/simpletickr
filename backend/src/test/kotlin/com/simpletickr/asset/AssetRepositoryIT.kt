package com.simpletickr.asset

import com.simpletickr.asset.model.Asset
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.price.model.PricePoint
import com.simpletickr.shared.CurrencyCode
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
@Import(AssetRepository::class, ListingRepository::class, AssetPriceHistoryRepository::class)
class AssetRepositoryIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: AssetRepository

    @Autowired
    private lateinit var listingRepository: ListingRepository

    @Autowired
    private lateinit var priceHistoryRepository: AssetPriceHistoryRepository

    private fun saveAssetWithListing(
        name: String = "Test Asset",
        type: AssetType = AssetType.STOCK,
        ticker: String = "TST",
        currency: String = "USD",
    ): Asset {
        val asset = repository.save(null, name, type)
        listingRepository.save(asset.id, null, ticker, CurrencyCode(currency))
        return repository.findById(asset.id)!!
    }

    @Test
    fun `findAll returns created assets with listings`() {
        saveAssetWithListing(name = "Stock A", ticker = "STKA")
        saveAssetWithListing(name = "Stock B", ticker = "STKB")
        val assets = repository.findAll()
        assertTrue(assets.size >= 2)
        assertTrue(assets.all { it.listings.isNotEmpty() })
    }

    @Test
    fun `save creates an asset and findById returns it with listing`() {
        val asset = saveAssetWithListing(name = "Test Stock", ticker = "TST_STOCK")
        assertTrue(asset.id > 0)
        assertEquals("Test Stock", asset.name)
        assertEquals(AssetType.STOCK, asset.type)
        assertEquals(1, asset.listings.size)
        assertEquals("TST_STOCK", asset.listings[0].ticker)
        assertEquals(CurrencyCode("USD"), asset.listings[0].currency)
        assertNull(asset.isin)
    }

    @Test
    fun `save stores isin when provided`() {
        val saved = repository.save("IE00B3RBWM25", "Vanguard FTSE All-World", AssetType.ETF)
        listingRepository.save(saved.id, "Euronext Amsterdam", "VWCE", CurrencyCode("EUR"))
        val found = repository.findById(saved.id)!!
        assertEquals("IE00B3RBWM25", found.isin)
        assertEquals("Euronext Amsterdam", found.listings[0].exchange)
    }

    @Test
    fun `findById returns null when asset does not exist`() {
        assertNull(repository.findById(-1L))
    }

    @Test
    fun `update changes asset fields`() {
        val asset = saveAssetWithListing()
        val updated = repository.update(asset.id, "US1234567890", "Renamed Asset", AssetType.ETF)
        assertNotNull(updated)
        assertEquals("Renamed Asset", updated.name)
        assertEquals(AssetType.ETF, updated.type)
        assertEquals("US1234567890", updated.isin)
    }

    @Test
    fun `update returns null when asset does not exist`() {
        assertNull(repository.update(-1L, null, "X", AssetType.OTHER))
    }

    @Test
    fun `delete removes the asset and its listings`() {
        val asset = saveAssetWithListing(ticker = "TST_DEL")
        repository.delete(asset.id)
        assertNull(repository.findById(asset.id))
    }

    @Test
    fun `findAllWithLatestPrice returns null price fields when no price history`() {
        val asset = repository.save(null, "No Price Asset", AssetType.STOCK)
        listingRepository.save(asset.id, null, "NPA", CurrencyCode("USD"))

        val results = repository.findAllWithLatestPrice()
        val found = results.find { it.id == asset.id }

        assertNotNull(found)
        assertEquals(1, found.listings.size)
        assertNull(found.listings[0].lastPriceDate)
        assertNull(found.listings[0].lastPrice)
    }

    @Test
    fun `findAllWithLatestPrice returns latest price date and price`() {
        val asset = repository.save(null, "Priced Asset", AssetType.STOCK)
        val listing = listingRepository.save(asset.id, null, "PRC", CurrencyCode("USD"))
        val older = LocalDate.of(2026, 1, 1)
        val newer = LocalDate.of(2026, 6, 1)
        priceHistoryRepository.upsert(listing.id, listOf(
            PricePoint(older, BigDecimal("100.00")),
            PricePoint(newer, BigDecimal("150.00")),
        ))

        val results = repository.findAllWithLatestPrice()
        val found = results.find { it.id == asset.id }

        assertNotNull(found)
        val l = found.listings[0]
        assertEquals(newer, l.lastPriceDate)
        assertEquals(0, BigDecimal("150.00").compareTo(l.lastPrice))
    }

    @Test
    fun `findAllWithLatestPrice returns correct prices per listing when asset has multiple listings`() {
        val asset = repository.save(null, "Multi-listing Asset", AssetType.ETF)
        val listing1 = listingRepository.save(asset.id, "NYSE", "ML1", CurrencyCode("USD"))
        val listing2 = listingRepository.save(asset.id, "LSE", "ML2", CurrencyCode("GBP"))
        val date = LocalDate.of(2026, 6, 15)
        priceHistoryRepository.upsert(listing1.id, listOf(PricePoint(date, BigDecimal("200.00"))))

        val results = repository.findAllWithLatestPrice()
        val found = results.find { it.id == asset.id }

        assertNotNull(found)
        assertEquals(2, found.listings.size)
        val l1 = found.listings.find { it.ticker == "ML1" }!!
        val l2 = found.listings.find { it.ticker == "ML2" }!!
        assertEquals(date, l1.lastPriceDate)
        assertEquals(0, BigDecimal("200.00").compareTo(l1.lastPrice))
        assertNull(l2.lastPriceDate)
        assertNull(l2.lastPrice)
    }
}
