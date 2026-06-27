package com.simpletickr.price.usecase

import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.price.provider.PriceProvider
import com.simpletickr.sync.SyncHistoryRepository
import com.simpletickr.sync.SyncStatus
import com.simpletickr.sync.SyncTrigger
import com.simpletickr.sync.SyncType
import com.simpletickr.transaction.persistence.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class BackfillPortfolioPricesUseCase(
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val mappingRepository: PriceProviderMappingRepository,
    private val providers: List<PriceProvider>,
    private val historyRepository: AssetPriceHistoryRepository,
    private val syncHistoryRepository: SyncHistoryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(portfolioId: Long): SyncResult? {
        if (portfolioRepository.findById(portfolioId) == null) return null

        val oldestDate = transactionRepository.findOldestTransactionDate(portfolioId)
            ?: return SyncResult(0, 0)
        val today = LocalDate.now()

        val listingIds = transactionRepository.findDistinctListingIds(portfolioId)
        if (listingIds.isEmpty()) return SyncResult(0, 0)

        log.info("Backfilling prices for portfolio={} from={} listings={}", portfolioId, oldestDate, listingIds.size)
        val startedAt = System.currentTimeMillis()
        val mappingsByListing = mappingRepository.findByListingIds(listingIds)

        var synced = 0
        var failed = 0
        for (listingId in listingIds) {
            val mapping = mappingsByListing[listingId]?.firstOrNull()
            if (mapping == null) {
                log.warn("No price mapping for listing={}, skipping", listingId)
                failed++
                continue
            }
            val provider = providers.find { it.name == mapping.provider }
            if (provider == null) {
                log.warn("No provider registered for '{}', skipping listing={}", mapping.provider, listingId)
                failed++
                continue
            }
            val from = resolveFrom(listingId, oldestDate)
            if (from > today) {
                log.info("Price history for listing={} is up to date, skipping", listingId)
                synced++
                continue
            }
            val points = provider.fetchHistory(mapping.externalId, from, today)
            if (points.isEmpty()) {
                log.warn("No prices returned for {} ({})", mapping.externalId, mapping.provider)
                failed++
                continue
            }
            historyRepository.upsert(listingId, points)
            log.info("Upserted {} price points for listing={} from={}", points.size, listingId, from)
            synced++
        }

        val result = SyncResult(synced, failed)

        val status = when {
            result.failed == 0 -> SyncStatus.SUCCESS
            result.synced == 0 -> SyncStatus.FAILED
            else -> SyncStatus.PARTIAL
        }
        syncHistoryRepository.record(SyncType.PRICE, SyncTrigger.MANUAL, status, System.currentTimeMillis() - startedAt, synced, failed)
        return result
    }

    // Determines the earliest date to fetch for a listing.
    // If a full backfill has already been done (earliest stored price covers oldestTransactionDate),
    // only fetch the gap since the last stored date. Otherwise fetch from oldestTransactionDate.
    private fun resolveFrom(listingId: Long, oldestTransactionDate: LocalDate): LocalDate {
        val earliest = historyRepository.findEarliestByListingId(listingId)
        if (earliest != null && !earliest.date.isAfter(oldestTransactionDate)) {
            val latest = historyRepository.findLatestByListingId(listingId)!!
            return latest.date.plusDays(1)
        }
        return oldestTransactionDate
    }
}
