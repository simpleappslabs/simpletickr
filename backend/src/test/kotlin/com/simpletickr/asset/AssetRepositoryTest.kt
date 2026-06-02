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
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AssetRepository::class)
class AssetRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: AssetRepository

    @Test
    fun `findAll returns empty list when no assets exist`() {
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `save creates an asset and returns it with a generated id`() {
        val asset = repository.save("AAPL", "Apple Inc.", AssetType.STOCK, "USD", null)
        assertTrue(asset.id > 0)
        assertEquals("AAPL", asset.ticker)
        assertEquals("Apple Inc.", asset.name)
        assertEquals(AssetType.STOCK, asset.type)
        assertEquals("USD", asset.currency)
        assertNull(asset.currentPrice)
    }

    @Test
    fun `save stores current price when provided`() {
        val price = BigDecimal("182.50")
        val asset = repository.save("MSFT", "Microsoft", AssetType.STOCK, "USD", price)
        assertEquals(0, price.compareTo(asset.currentPrice))
    }

    @Test
    fun `findById returns the asset when it exists`() {
        val saved = repository.save("ETH", "Ethereum", AssetType.CRYPTO, "USD", null)
        val found = repository.findById(saved.id)
        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("ETH", found.ticker)
    }

    @Test
    fun `findById returns null when asset does not exist`() {
        assertNull(repository.findById(-1L))
    }

    @Test
    fun `update changes asset fields and returns updated asset`() {
        val saved = repository.save("BTC", "Bitcoin", AssetType.CRYPTO, "USD", null)
        val updated = repository.update(saved.id, "BTC", "Bitcoin", AssetType.CRYPTO, "USD", BigDecimal("50000"))
        assertNotNull(updated)
        assertEquals(0, BigDecimal("50000").compareTo(updated.currentPrice))
    }

    @Test
    fun `update returns null when asset does not exist`() {
        assertNull(repository.update(-1L, "X", "X", AssetType.OTHER, "USD", null))
    }

    @Test
    fun `delete removes the asset`() {
        val saved = repository.save("VTI", "Vanguard Total Market ETF", AssetType.ETF, "USD", null)
        repository.delete(saved.id)
        assertNull(repository.findById(saved.id))
    }
}