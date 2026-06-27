package com.simpletickr.price.usecase

import com.simpletickr.price.model.PriceProviderMapping
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.price.provider.PriceProvider
import com.simpletickr.sync.SyncHistoryRepository
import com.simpletickr.sync.SyncStatus
import com.simpletickr.sync.SyncTrigger
import com.simpletickr.sync.SyncType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate

data class SyncResult(val synced: Int, val failed: Int)

@Service
class SyncPricesUseCase(
    private val providers: List<PriceProvider>,
    private val mappingRepository: PriceProviderMappingRepository,
    private val historyRepository: AssetPriceHistoryRepository,
    private val syncHistoryRepository: SyncHistoryRepository,
    @Value("\${price.sync.lookback-days:30}") private val lookbackDays: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(from: LocalDate? = null, to: LocalDate? = null, trigger: SyncTrigger = SyncTrigger.MANUAL, listingId: Long? = null): SyncResult {
        log.info("Syncing prices: trigger={}", trigger)
        val startedAt = System.currentTimeMillis()
        val effectiveFrom = from ?: LocalDate.now().minusDays(lookbackDays)
        val effectiveTo = to ?: LocalDate.now()
        val mappings = if (listingId != null) mappingRepository.findByListingId(listingId) else mappingRepository.findAll()
        var synced = 0
        var failed = 0

        for (mapping in mappings) {
            val provider = providers.find { it.name == mapping.provider }
            if (provider == null) {
                log.warn("No provider registered for '{}', skipping listing {}", mapping.provider, mapping.listingId)
                failed++
                continue
            }
            val points = provider.fetchHistory(mapping.externalId, effectiveFrom, effectiveTo)
            if (points.isNotEmpty()) {
                historyRepository.upsert(mapping.listingId, points)
                synced++
            } else {
                log.warn("No price returned for {} ({})", mapping.externalId, mapping.provider)
                failed++
            }
        }

        val result = SyncResult(synced, failed)
        val status = when {
            result.failed == 0 -> SyncStatus.SUCCESS
            result.synced == 0 -> SyncStatus.FAILED
            else -> SyncStatus.PARTIAL
        }
        syncHistoryRepository.record(SyncType.PRICE, trigger, status, System.currentTimeMillis() - startedAt, synced, failed)
        return result
    }
}
