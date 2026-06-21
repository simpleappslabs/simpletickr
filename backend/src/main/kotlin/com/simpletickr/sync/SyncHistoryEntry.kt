package com.simpletickr.sync

import java.time.OffsetDateTime

enum class SyncType { PRICE, FX }
enum class SyncTrigger { MANUAL, SCHEDULED }
enum class SyncStatus { SUCCESS, FAILED, PARTIAL }

data class SyncHistoryEntry(
    val id: Long,
    val type: SyncType,
    val trigger: SyncTrigger,
    val status: SyncStatus,
    val startedAt: OffsetDateTime,
    val durationMs: Long,
    val synced: Int,
    val failed: Int,
)
