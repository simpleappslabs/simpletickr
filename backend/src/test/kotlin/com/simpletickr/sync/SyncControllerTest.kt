package com.simpletickr.sync

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime

@WebMvcTest(SyncController::class)
class SyncControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var syncHistoryRepository: SyncHistoryRepository

    private fun entry(
        id: Long,
        type: SyncType = SyncType.PRICE,
        trigger: SyncTrigger = SyncTrigger.MANUAL,
        status: SyncStatus = SyncStatus.SUCCESS,
        durationMs: Long = 1000L,
        synced: Int = 5,
        failed: Int = 0,
    ) = SyncHistoryEntry(id, type, trigger, status, OffsetDateTime.now(), durationMs, synced, failed)

    @Test
    fun `GET sync history returns 200 with entries for PRICE type`() {
        whenever(syncHistoryRepository.findRecent(SyncType.PRICE)).thenReturn(
            listOf(entry(1L, synced = 10, failed = 2, status = SyncStatus.PARTIAL, durationMs = 2500L))
        )

        mockMvc.perform(get("/sync/history").param("type", "PRICE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].type").value("PRICE"))
            .andExpect(jsonPath("$[0].trigger").value("MANUAL"))
            .andExpect(jsonPath("$[0].status").value("PARTIAL"))
            .andExpect(jsonPath("$[0].synced").value(10))
            .andExpect(jsonPath("$[0].failed").value(2))
            .andExpect(jsonPath("$[0].durationMs").value(2500))
    }

    @Test
    fun `GET sync history returns 200 with entries for FX type`() {
        whenever(syncHistoryRepository.findRecent(SyncType.FX)).thenReturn(
            listOf(entry(2L, type = SyncType.FX, trigger = SyncTrigger.SCHEDULED, status = SyncStatus.SUCCESS))
        )

        mockMvc.perform(get("/sync/history").param("type", "FX"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("FX"))
            .andExpect(jsonPath("$[0].trigger").value("SCHEDULED"))
            .andExpect(jsonPath("$[0].status").value("SUCCESS"))
    }

    @Test
    fun `GET sync history returns 200 with empty list when no history`() {
        whenever(syncHistoryRepository.findRecent(SyncType.PRICE)).thenReturn(emptyList())

        mockMvc.perform(get("/sync/history").param("type", "PRICE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET sync history returns 400 for unknown type`() {
        mockMvc.perform(get("/sync/history").param("type", "GARBAGE"))
            .andExpect(status().isBadRequest)
    }
}
