package com.simpletickr.fx

import com.simpletickr.fx.usecase.SyncFxRatesUseCase
import com.simpletickr.sync.SyncTrigger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FxRateSyncJob(private val syncFxRatesUseCase: SyncFxRatesUseCase) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 22 * * MON-FRI")
    fun run() {
        val result = syncFxRatesUseCase.execute(trigger = SyncTrigger.SCHEDULED)
        log.info("Scheduled FX rate sync: synced={}, failed={}", result.synced, result.failed)
    }
}
