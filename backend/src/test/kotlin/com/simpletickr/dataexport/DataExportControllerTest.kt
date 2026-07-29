package com.simpletickr.dataexport

import com.fasterxml.jackson.databind.ObjectMapper
import com.simpletickr.auth.CurrentUser
import com.simpletickr.dataexport.model.ImportAnalysis
import com.simpletickr.dataexport.model.ImportResult
import com.simpletickr.dataexport.model.SimpletickrExport
import com.simpletickr.dataexport.model.SettingsExport
import org.junit.jupiter.api.Test
import com.simpletickr.shared.SecurityConfig
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(DataExportController::class)
@Import(SecurityConfig::class)
class DataExportControllerTest {

    private val owner = CurrentUser(1L, "test-user", "hash")

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockitoBean private lateinit var exportService: ExportService
    @MockitoBean private lateinit var importDataUseCase: ImportDataUseCase

    private val emptyExport = SimpletickrExport(
        schemaVersion = 1,
        exportedAt = Instant.now(),
        settings = SettingsExport("EUR"),
        assets = emptyList(),
        portfolios = emptyList(),
    )

    private val jsonFile = MockMultipartFile(
        "file", "export.json", MediaType.APPLICATION_JSON_VALUE, "{}".toByteArray()
    )

    @Test
    fun `GET data-export returns JSON attachment with correct headers`() {
        whenever(exportService.buildExport(1L, null)).thenReturn(emptyExport)

        mockMvc.perform(get("/data-export").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("simpletickr-export")))
    }

    @Test
    fun `GET data-export includes schemaVersion in response body`() {
        whenever(exportService.buildExport(1L, null)).thenReturn(emptyExport)

        mockMvc.perform(get("/data-export").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.schemaVersion").value(1))
    }

    @Test
    fun `POST data-import with dryRun=true calls analyze and returns ImportAnalysis`() {
        val analysis = ImportAnalysis(
            errors = emptyList(),
            assetsToCreate = 2, assetsExisting = 1,
            listingsToCreate = 3, listingsExisting = 2,
            portfoliosToCreate = 1, portfoliosExisting = 0,
            transactionsToImport = 10, transactionsSkipped = 0,
        )
        whenever(importDataUseCase.analyze(any(), any())).thenReturn(analysis)

        mockMvc.perform(multipart("/data-import").file(jsonFile).param("dryRun", "true").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assetsToCreate").value(2))
            .andExpect(jsonPath("$.portfoliosToCreate").value(1))
            .andExpect(jsonPath("$.transactionsToImport").value(10))
            .andExpect(jsonPath("$.errors").isArray)
    }

    @Test
    fun `POST data-import without dryRun calls apply and returns ImportResult`() {
        val result = ImportResult(
            assetsCreated = 2, listingsCreated = 3,
            portfoliosCreated = 1, transactionsImported = 10,
        )
        whenever(importDataUseCase.apply(any(), any())).thenReturn(result)

        mockMvc.perform(multipart("/data-import").file(jsonFile).with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assetsCreated").value(2))
            .andExpect(jsonPath("$.listingsCreated").value(3))
            .andExpect(jsonPath("$.portfoliosCreated").value(1))
            .andExpect(jsonPath("$.transactionsImported").value(10))
    }
}
