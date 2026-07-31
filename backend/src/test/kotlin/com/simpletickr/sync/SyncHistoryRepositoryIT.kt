package com.simpletickr.sync

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
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(SyncHistoryRepository::class)
class SyncHistoryRepositoryIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: SyncHistoryRepository

    @Test
    fun `record and findRecent round-trips a sync history entry`() {
        repository.record(SyncType.PRICE, SyncTrigger.MANUAL, SyncStatus.SUCCESS, 1234L, 10, 0)

        val entries = repository.findRecent(SyncType.PRICE)

        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals(SyncType.PRICE, e.type)
        assertEquals(SyncTrigger.MANUAL, e.trigger)
        assertEquals(SyncStatus.SUCCESS, e.status)
        assertEquals(1234L, e.durationMs)
        assertEquals(10, e.synced)
        assertEquals(0, e.failed)
    }

    @Test
    fun `findRecent returns entries newest first`() {
        repository.record(SyncType.FX, SyncTrigger.SCHEDULED, SyncStatus.SUCCESS, 100L, 1, 0)
        repository.record(SyncType.FX, SyncTrigger.MANUAL, SyncStatus.PARTIAL, 200L, 2, 1)

        val entries = repository.findRecent(SyncType.FX)

        assertEquals(2, entries.size)
        assertEquals(SyncTrigger.MANUAL, entries[0].trigger)
        assertEquals(SyncTrigger.SCHEDULED, entries[1].trigger)
    }

    @Test
    fun `findRecent only returns entries for the requested type`() {
        repository.record(SyncType.PRICE, SyncTrigger.MANUAL, SyncStatus.SUCCESS, 10L, 5, 0)
        repository.record(SyncType.FX, SyncTrigger.MANUAL, SyncStatus.SUCCESS, 20L, 1, 0)

        val priceEntries = repository.findRecent(SyncType.PRICE)
        val fxEntries = repository.findRecent(SyncType.FX)

        assertTrue(priceEntries.all { it.type == SyncType.PRICE })
        assertTrue(fxEntries.all { it.type == SyncType.FX })
    }

    @Test
    fun `findRecent respects the limit`() {
        repeat(25) { i ->
            repository.record(SyncType.PRICE, SyncTrigger.SCHEDULED, SyncStatus.SUCCESS, i.toLong(), i, 0)
        }

        val entries = repository.findRecent(SyncType.PRICE, limit = 20)

        assertEquals(20, entries.size)
    }

    @Test
    fun `findRecent returns empty list when no history exists`() {
        val entries = repository.findRecent(SyncType.FX)

        assertTrue(entries.isEmpty())
    }
}
