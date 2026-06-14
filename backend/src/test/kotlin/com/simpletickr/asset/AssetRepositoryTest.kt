package com.simpletickr.asset

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AssetRepository::class, ListingRepository::class)
class AssetRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: AssetRepository

    @Autowired
    private lateinit var listingRepository: ListingRepository

    private fun saveAssetWithListing(
        name: String = "Test Asset",
        type: AssetType = AssetType.STOCK,
        ticker: String = "TST",
        currency: String = "USD",
    ): Asset {
        val asset = repository.save(null, name, type)
        listingRepository.save(asset.id, null, ticker, currency)
        return repository.findById(asset.id)!!
    }

    @Test
    fun `findAll returns seeded assets with listings`() {
        val assets = repository.findAll()
        assertTrue(assets.isNotEmpty())
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
        assertEquals("USD", asset.listings[0].currency)
        assertNull(asset.isin)
    }

    @Test
    fun `save stores isin when provided`() {
        val saved = repository.save("IE00B3RBWM25", "Vanguard FTSE All-World", AssetType.ETF)
        listingRepository.save(saved.id, "Euronext Amsterdam", "VWCE", "EUR")
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
}
