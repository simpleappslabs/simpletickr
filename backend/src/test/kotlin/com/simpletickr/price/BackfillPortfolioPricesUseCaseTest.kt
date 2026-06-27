package com.simpletickr.price

import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.price.model.PricePoint
import com.simpletickr.price.model.PriceProviderMapping
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.price.provider.PriceProvider
import com.simpletickr.price.usecase.BackfillPortfolioPricesUseCase
import com.simpletickr.price.usecase.SyncResult
import com.simpletickr.sync.SyncHistoryRepository
import com.simpletickr.sync.SyncStatus
import com.simpletickr.sync.SyncTrigger
import com.simpletickr.sync.SyncType
import com.simpletickr.transaction.persistence.TransactionRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.argumentCaptor
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackfillPortfolioPricesUseCaseTest {

    private val portfolioRepository = mock<PortfolioRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val mappingRepository = mock<PriceProviderMappingRepository>()
    private val provider = mock<PriceProvider>()
    private val historyRepository = mock<AssetPriceHistoryRepository>()
    private val syncHistoryRepository = mock<SyncHistoryRepository>()
    private val useCase = BackfillPortfolioPricesUseCase(
        portfolioRepository, transactionRepository, mappingRepository,
        listOf(provider), historyRepository, syncHistoryRepository,
    )

    private val portfolio = Portfolio(1L, "Test")
    private val oldestDate = LocalDate.of(2018, 3, 14)
    private val mapping = PriceProviderMapping(1L, 10L, "YAHOO", "VWCE.DE")
    private val pricePoint = PricePoint(LocalDate.of(2024, 1, 15), BigDecimal("100.00"))

    init {
        whenever(provider.name).thenReturn("YAHOO")
    }

    @Test
    fun `returns null when portfolio does not exist`() {
        whenever(portfolioRepository.findById(99L)).thenReturn(null)

        assertNull(useCase.execute(99L))
        verify(transactionRepository, never()).findOldestTransactionDate(any())
    }

    @Test
    fun `returns SyncResult(0,0) when portfolio has no transactions`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(null)

        val result = useCase.execute(1L)!!
        assertEquals(0, result.synced)
        assertEquals(0, result.failed)
        verify(historyRepository, never()).upsert(any(), any())
    }

    @Test
    fun `fetches from oldest transaction date when no history is stored`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(oldestDate)
        whenever(transactionRepository.findDistinctListingIds(1L)).thenReturn(listOf(10L))
        whenever(mappingRepository.findByListingIds(listOf(10L))).thenReturn(mapOf(10L to listOf(mapping)))
        whenever(historyRepository.findEarliestByListingId(10L)).thenReturn(null)
        whenever(provider.fetchHistory(eq("VWCE.DE"), eq(oldestDate), any())).thenReturn(listOf(pricePoint))

        val result = useCase.execute(1L)!!

        assertEquals(1, result.synced)
        assertEquals(0, result.failed)
        verify(historyRepository).upsert(10L, listOf(pricePoint))
        verify(syncHistoryRepository).record(eq(SyncType.PRICE), eq(SyncTrigger.MANUAL), eq(SyncStatus.SUCCESS), any(), eq(1), eq(0))
    }

    @Test
    fun `fetches from oldest transaction date when only recent history exists (e g from 30-day sync)`() {
        val recentDate = LocalDate.now().minusDays(15)
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(oldestDate)
        whenever(transactionRepository.findDistinctListingIds(1L)).thenReturn(listOf(10L))
        whenever(mappingRepository.findByListingIds(listOf(10L))).thenReturn(mapOf(10L to listOf(mapping)))
        // Earliest stored is recent — full backfill not yet done
        whenever(historyRepository.findEarliestByListingId(10L)).thenReturn(PricePoint(recentDate, BigDecimal("98")))
        whenever(provider.fetchHistory(eq("VWCE.DE"), eq(oldestDate), any())).thenReturn(listOf(pricePoint))

        val result = useCase.execute(1L)!!

        assertEquals(1, result.synced)
        val captor = argumentCaptor<LocalDate>()
        verify(provider).fetchHistory(any(), captor.capture(), any())
        assertEquals(oldestDate, captor.firstValue)
    }

    @Test
    fun `fetches only the gap since last stored date when full backfill already done`() {
        val latestStored = LocalDate.now().minusDays(2)
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(oldestDate)
        whenever(transactionRepository.findDistinctListingIds(1L)).thenReturn(listOf(10L))
        whenever(mappingRepository.findByListingIds(listOf(10L))).thenReturn(mapOf(10L to listOf(mapping)))
        // Earliest stored covers back to oldestDate — full backfill already done
        whenever(historyRepository.findEarliestByListingId(10L)).thenReturn(PricePoint(oldestDate, BigDecimal("50")))
        whenever(historyRepository.findLatestByListingId(10L)).thenReturn(PricePoint(latestStored, BigDecimal("102")))
        whenever(provider.fetchHistory(eq("VWCE.DE"), eq(latestStored.plusDays(1)), any())).thenReturn(listOf(pricePoint))

        val result = useCase.execute(1L)!!

        assertEquals(1, result.synced)
        val captor = argumentCaptor<LocalDate>()
        verify(provider).fetchHistory(any(), captor.capture(), any())
        assertEquals(latestStored.plusDays(1), captor.firstValue)
    }

    @Test
    fun `skips fetch when history is already up to date`() {
        val today = LocalDate.now()
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(oldestDate)
        whenever(transactionRepository.findDistinctListingIds(1L)).thenReturn(listOf(10L))
        whenever(mappingRepository.findByListingIds(listOf(10L))).thenReturn(mapOf(10L to listOf(mapping)))
        whenever(historyRepository.findEarliestByListingId(10L)).thenReturn(PricePoint(oldestDate, BigDecimal("50")))
        whenever(historyRepository.findLatestByListingId(10L)).thenReturn(PricePoint(today, BigDecimal("102")))

        val result = useCase.execute(1L)!!

        assertEquals(1, result.synced)
        verify(provider, never()).fetchHistory(any(), any(), any())
        verify(historyRepository, never()).upsert(any(), any())
    }

    @Test
    fun `counts listing with no mapping as failed`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(oldestDate)
        whenever(transactionRepository.findDistinctListingIds(1L)).thenReturn(listOf(10L))
        whenever(mappingRepository.findByListingIds(listOf(10L))).thenReturn(emptyMap())

        val result = useCase.execute(1L)!!

        assertEquals(0, result.synced)
        assertEquals(1, result.failed)
        verify(historyRepository, never()).upsert(any(), any())
    }

    @Test
    fun `counts listing with empty provider response as failed`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(oldestDate)
        whenever(transactionRepository.findDistinctListingIds(1L)).thenReturn(listOf(10L))
        whenever(mappingRepository.findByListingIds(listOf(10L))).thenReturn(mapOf(10L to listOf(mapping)))
        whenever(provider.fetchHistory(any(), any(), any())).thenReturn(emptyList())

        val result = useCase.execute(1L)!!

        assertEquals(0, result.synced)
        assertEquals(1, result.failed)
    }

    @Test
    fun `aggregates synced and failed counts across multiple listings`() {
        val mapping2 = PriceProviderMapping(2L, 11L, "YAHOO", "IWDA.AS")
        whenever(portfolioRepository.findById(1L)).thenReturn(portfolio)
        whenever(transactionRepository.findOldestTransactionDate(1L)).thenReturn(oldestDate)
        whenever(transactionRepository.findDistinctListingIds(1L)).thenReturn(listOf(10L, 11L))
        whenever(mappingRepository.findByListingIds(listOf(10L, 11L))).thenReturn(
            mapOf(10L to listOf(mapping), 11L to listOf(mapping2))
        )
        whenever(provider.fetchHistory(eq("VWCE.DE"), any(), any())).thenReturn(listOf(pricePoint))
        whenever(provider.fetchHistory(eq("IWDA.AS"), any(), any())).thenReturn(emptyList())

        val result = useCase.execute(1L)!!

        assertEquals(1, result.synced)
        assertEquals(1, result.failed)
        verify(syncHistoryRepository).record(eq(SyncType.PRICE), any(), eq(SyncStatus.PARTIAL), any(), eq(1), eq(1))
    }
}
