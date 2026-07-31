package com.simpletickr.brokerimport

import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.model.AssetType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AssetImportMappingRepository::class, AssetRepository::class)
class AssetImportMappingRepositoryIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired private lateinit var repository: AssetImportMappingRepository
    @Autowired private lateinit var assetRepository: AssetRepository

    private var assetId: Long = 0

    @BeforeEach
    fun setup() {
        assetId = assetRepository.save(null, "Test Asset", AssetType.ETF).id
    }

    @Test
    fun `save and findByBrokerAndName return the mapping`() {
        repository.save("bolero", "ISHAR.III PLC CORE MSCI WORLD (AS)", assetId)

        val found = repository.findByBrokerAndName("bolero", "ISHAR.III PLC CORE MSCI WORLD (AS)")

        assertNotNull(found)
        assertEquals("bolero", found.broker)
        assertEquals("ISHAR.III PLC CORE MSCI WORLD (AS)", found.externalName)
        assertEquals(assetId, found.assetId)
        assertTrue(found.id > 0)
    }

    @Test
    fun `findByBrokerAndName returns null when not found`() {
        val found = repository.findByBrokerAndName("bolero", "NON_EXISTENT")
        assertNull(found)
    }

    @Test
    fun `unique constraint prevents duplicate broker + external_name`() {
        repository.save("bolero", "INSTRUMENT X", assetId)

        assertFailsWith<DuplicateKeyException> {
            repository.save("bolero", "INSTRUMENT X", assetId)
        }
    }

    @Test
    fun `different brokers can have the same external_name`() {
        repository.save("bolero", "INSTRUMENT X", assetId)
        val second = repository.save("other_broker", "INSTRUMENT X", assetId)

        assertNotNull(second)
        assertEquals("other_broker", second.broker)
    }

    @Test
    fun `delete removes the mapping`() {
        val saved = repository.save("bolero", "TO DELETE", assetId)

        repository.delete(saved.id)

        assertNull(repository.findByBrokerAndName("bolero", "TO DELETE"))
    }

    @Test
    fun `findAll with broker filter returns only matching mappings`() {
        repository.save("bolero", "INSTRUMENT A", assetId)
        repository.save("other", "INSTRUMENT B", assetId)

        val boleroMappings = repository.findAll("bolero")

        assertEquals(1, boleroMappings.size)
        assertEquals("INSTRUMENT A", boleroMappings[0].externalName)
    }
}
