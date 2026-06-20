package com.simpletickr.price

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

data class SyncResult(val synced: Int, val failed: Int)

@Service
class PriceService(
    private val providers: List<PriceProvider>,
    private val mappingRepository: PriceProviderMappingRepository,
    private val historyRepository: AssetPriceHistoryRepository,
    @Value("\${price.sync.lookback-days:30}") private val lookbackDays: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 22 * * MON-FRI")
    fun scheduledSync() {
        val result = syncAll()
        log.info("Scheduled price sync: synced={}, failed={}", result.synced, result.failed)
    }

    fun syncAll(from: LocalDate? = null, to: LocalDate? = null): SyncResult {
        val effectiveFrom = from ?: LocalDate.now().minusDays(lookbackDays)
        val effectiveTo = to ?: LocalDate.now()
        val mappings = mappingRepository.findAll()
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

        return SyncResult(synced, failed)
    }
}
