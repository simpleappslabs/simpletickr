package com.simpletickr.sync

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class SyncHistoryRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<SyncHistoryEntry> { rs, _ ->
        SyncHistoryEntry(
            id = rs.getLong("id"),
            type = SyncType.valueOf(rs.getString("type")),
            trigger = SyncTrigger.valueOf(rs.getString("trigger")),
            status = SyncStatus.valueOf(rs.getString("status")),
            startedAt = rs.getObject("started_at", OffsetDateTime::class.java),
            durationMs = rs.getLong("duration_ms"),
            synced = rs.getInt("synced"),
            failed = rs.getInt("failed"),
        )
    }

    fun record(type: SyncType, trigger: SyncTrigger, status: SyncStatus, durationMs: Long, synced: Int, failed: Int) {
        jdbcTemplate.update(
            "INSERT INTO sync_history (type, trigger, status, duration_ms, synced, failed) VALUES (?, ?, ?, ?, ?, ?)",
            type.name, trigger.name, status.name, durationMs, synced, failed,
        )
    }

    fun findRecent(type: SyncType, limit: Int = 20): List<SyncHistoryEntry> =
        jdbcTemplate.query(
            """SELECT id, type, trigger, status, started_at, duration_ms, synced, failed
               FROM sync_history
               WHERE type = ?
               ORDER BY started_at DESC, id DESC
               LIMIT ?""",
            rowMapper,
            type.name, limit,
        )
}
