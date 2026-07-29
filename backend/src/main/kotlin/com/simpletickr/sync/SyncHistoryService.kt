package com.simpletickr.sync

import org.springframework.stereotype.Service

@Service
class SyncHistoryService(private val syncHistoryRepository: SyncHistoryRepository) {

    fun findRecent(type: SyncType, limit: Int): List<SyncHistoryEntry> = syncHistoryRepository.findRecent(type, limit)
}
