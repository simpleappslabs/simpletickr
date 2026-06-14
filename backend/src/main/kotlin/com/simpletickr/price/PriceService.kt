package com.simpletickr.price

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

data class SyncResult(val synced: Int, val failed: Int)

@Service
class PriceService(
    private val providers: List<PriceProvider>,
    private val mappingRepository: PriceProviderMappingRepository,
    private val historyRepository: AssetPriceHistoryRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 22 * * MON-FRI")
    fun scheduledSync() {
        val result = syncAll()
        log.info("Scheduled price sync: synced={}, failed={}", result.synced, result.failed)
    }

    fun syncAll(): SyncResult {
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
            val point = provider.fetchLatest(mapping.externalId)
            if (point != null) {
                historyRepository.upsert(mapping.listingId, listOf(point))
                synced++
            } else {
                log.warn("No price returned for {} ({})", mapping.externalId, mapping.provider)
                failed++
            }
        }

        return SyncResult(synced, failed)
    }
}
