package com.simpletickr.price

import com.simpletickr.sync.SyncHistoryRepository
import com.simpletickr.sync.SyncStatus
import com.simpletickr.sync.SyncTrigger
import com.simpletickr.sync.SyncType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class PriceServiceTest {

    private val providers = listOf(mock<PriceProvider>())
    private val mappingRepository = mock<PriceProviderMappingRepository>()
    private val historyRepository = mock<AssetPriceHistoryRepository>()
    private val syncHistoryRepository = mock<SyncHistoryRepository>()
    private val service = PriceService(providers, mappingRepository, historyRepository, syncHistoryRepository, 30L)

    private val provider = providers[0]
    private val mapping = PriceProviderMapping(1L, 10L, "YAHOO", "AAPL")
    private val pricePoint = PricePoint(LocalDate.now(), BigDecimal("150.00"))

    init {
        whenever(provider.name).thenReturn("YAHOO")
    }

    @Test
    fun `syncAll records SUCCESS when all listings sync`() {
        whenever(mappingRepository.findAll()).thenReturn(listOf(mapping))
        whenever(provider.fetchHistory(any(), any(), any())).thenReturn(listOf(pricePoint))

        val result = service.syncAll(trigger = SyncTrigger.MANUAL)

        assertEquals(1, result.synced)
        assertEquals(0, result.failed)
        verify(syncHistoryRepository).record(
            eq(SyncType.PRICE), eq(SyncTrigger.MANUAL), eq(SyncStatus.SUCCESS), any(), eq(1), eq(0)
        )
    }

    @Test
    fun `syncAll records FAILED when all listings fail`() {
        whenever(mappingRepository.findAll()).thenReturn(listOf(mapping))
        whenever(provider.fetchHistory(any(), any(), any())).thenReturn(emptyList())

        val result = service.syncAll(trigger = SyncTrigger.SCHEDULED)

        assertEquals(0, result.synced)
        assertEquals(1, result.failed)
        verify(syncHistoryRepository).record(
            eq(SyncType.PRICE), eq(SyncTrigger.SCHEDULED), eq(SyncStatus.FAILED), any(), eq(0), eq(1)
        )
    }

    @Test
    fun `syncAll records PARTIAL when some listings fail`() {
        val mapping2 = PriceProviderMapping(2L, 11L, "YAHOO", "MSFT")
        whenever(mappingRepository.findAll()).thenReturn(listOf(mapping, mapping2))
        whenever(provider.fetchHistory(eq("AAPL"), any(), any())).thenReturn(listOf(pricePoint))
        whenever(provider.fetchHistory(eq("MSFT"), any(), any())).thenReturn(emptyList())

        val result = service.syncAll(trigger = SyncTrigger.MANUAL)

        assertEquals(1, result.synced)
        assertEquals(1, result.failed)
        verify(syncHistoryRepository).record(
            eq(SyncType.PRICE), eq(SyncTrigger.MANUAL), eq(SyncStatus.PARTIAL), any(), eq(1), eq(1)
        )
    }

    @Test
    fun `syncAll records SUCCESS with zero counts when no mappings exist`() {
        whenever(mappingRepository.findAll()).thenReturn(emptyList())

        service.syncAll(trigger = SyncTrigger.SCHEDULED)

        val statusCaptor = argumentCaptor<SyncStatus>()
        verify(syncHistoryRepository).record(
            eq(SyncType.PRICE), eq(SyncTrigger.SCHEDULED), statusCaptor.capture(), any(), eq(0), eq(0)
        )
        assertEquals(SyncStatus.SUCCESS, statusCaptor.firstValue)
    }
}
