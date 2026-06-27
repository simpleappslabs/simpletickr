package com.simpletickr.price

import com.simpletickr.price.usecase.SyncPricesUseCase
import com.simpletickr.sync.SyncTrigger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PriceSyncJob(private val syncPricesUseCase: SyncPricesUseCase) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 22 * * MON-FRI")
    fun run() {
        val result = syncPricesUseCase.execute(trigger = SyncTrigger.SCHEDULED)
        log.info("Scheduled price sync: synced={}, failed={}", result.synced, result.failed)
    }
}
