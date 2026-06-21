package com.simpletickr.sync

import com.simpletickr.generated.api.SyncApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.SyncHistoryEntry as SyncHistoryEntryModel
import com.simpletickr.generated.model.SyncStatus as SyncStatusModel
import com.simpletickr.generated.model.SyncTrigger as SyncTriggerModel
import com.simpletickr.generated.model.SyncType as SyncTypeModel

@RestController
class SyncController(
    private val syncHistoryRepository: SyncHistoryRepository,
) : SyncApi {

    override fun getSyncHistory(type: SyncTypeModel): ResponseEntity<List<SyncHistoryEntryModel>> {
        val syncType = SyncType.valueOf(type.name)
        return ResponseEntity.ok(syncHistoryRepository.findRecent(syncType).map { it.toModel() })
    }

    private fun SyncHistoryEntry.toModel() = SyncHistoryEntryModel(
        id = id,
        type = SyncTypeModel.valueOf(type.name),
        trigger = SyncTriggerModel.valueOf(trigger.name),
        status = SyncStatusModel.valueOf(status.name),
        startedAt = startedAt,
        durationMs = durationMs,
        synced = synced,
        failed = failed,
    )
}
