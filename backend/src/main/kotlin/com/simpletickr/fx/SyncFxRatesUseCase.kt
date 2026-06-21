package com.simpletickr.fx

import com.simpletickr.asset.ListingRepository
import com.simpletickr.price.SyncResult
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.sync.SyncHistoryRepository
import com.simpletickr.sync.SyncStatus
import com.simpletickr.sync.SyncTrigger
import com.simpletickr.sync.SyncType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SyncFxRatesUseCase(
    private val providers: List<FxRateProvider>,
    private val fxRateRepository: FxRateRepository,
    private val listingRepository: ListingRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val syncHistoryRepository: SyncHistoryRepository,
    @Value("\${price.sync.lookback-days:30}") private val lookbackDays: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(from: LocalDate? = null, to: LocalDate? = null, trigger: SyncTrigger = SyncTrigger.MANUAL): SyncResult {
        val startedAt = System.currentTimeMillis()
        val effectiveFrom = from ?: LocalDate.now().minusDays(lookbackDays)
        val effectiveTo = to ?: LocalDate.now()
        val baseCurrency = userSettingsRepository.find().baseCurrency
        val quoteCurrencies = listingRepository.findDistinctCurrencies().filter { it != baseCurrency }

        if (quoteCurrencies.isEmpty()) {
            syncHistoryRepository.record(SyncType.FX, trigger, SyncStatus.SUCCESS, System.currentTimeMillis() - startedAt, 0, 0)
            return SyncResult(0, 0)
        }

        val provider = providers.find { it.name == "YAHOO" }
        if (provider == null) {
            log.warn("No YAHOO FX provider registered, skipping sync")
            syncHistoryRepository.record(SyncType.FX, trigger, SyncStatus.FAILED, System.currentTimeMillis() - startedAt, 0, quoteCurrencies.size)
            return SyncResult(0, quoteCurrencies.size)
        }

        var synced = 0
        var failed = 0
        for (quoteCurrency in quoteCurrencies) {
            val rates = provider.fetchHistory(baseCurrency, quoteCurrency, effectiveFrom, effectiveTo)
            if (rates.isNotEmpty()) {
                fxRateRepository.upsert(rates)
                synced++
            } else {
                log.warn("No FX rates returned for {}/{}", baseCurrency, quoteCurrency)
                failed++
            }
        }

        val result = SyncResult(synced, failed)
        val status = when {
            result.failed == 0 -> SyncStatus.SUCCESS
            result.synced == 0 -> SyncStatus.FAILED
            else -> SyncStatus.PARTIAL
        }
        syncHistoryRepository.record(SyncType.FX, trigger, status, System.currentTimeMillis() - startedAt, synced, failed)
        return result
    }
}
