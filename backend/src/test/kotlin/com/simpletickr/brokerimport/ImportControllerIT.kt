package com.simpletickr.brokerimport

import com.fasterxml.jackson.databind.ObjectMapper
import com.simpletickr.brokerimport.bolero.AnalyzeBoleroImportUseCase
import com.simpletickr.brokerimport.bolero.AssetImportMappingRef
import com.simpletickr.brokerimport.bolero.BoleroAnalysisResult
import com.simpletickr.brokerimport.bolero.BoleroInstrumentInfo
import com.simpletickr.brokerimport.bolero.ImportBoleroTransactionsUseCase
import com.simpletickr.account.AccountService
import com.simpletickr.auth.CurrentUser
import com.simpletickr.portfolio.PortfolioQueryService
import com.simpletickr.shared.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ImportController::class)
@Import(SecurityConfig::class)
class ImportControllerIT {

    private val owner = CurrentUser(1L, "test-user", "hash")

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockitoBean private lateinit var analyzeBoleroImportUseCase: AnalyzeBoleroImportUseCase
    @MockitoBean private lateinit var importBoleroTransactionsUseCase: ImportBoleroTransactionsUseCase
    @MockitoBean private lateinit var assetImportMappingService: AssetImportMappingService
    @MockitoBean private lateinit var createAssetImportMappingUseCase: CreateAssetImportMappingUseCase
    @MockitoBean private lateinit var deleteAssetImportMappingUseCase: DeleteAssetImportMappingUseCase
    @MockitoBean private lateinit var portfolioQueryService: PortfolioQueryService
    @MockitoBean private lateinit var accountService: AccountService

    private val emptyXlsx = MockMultipartFile(
        "file", "test.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        byteArrayOf()
    )

    @Test
    fun `POST analyze returns analysis result`() {
        val analysisResult = BoleroAnalysisResult(
            instruments = listOf(
                BoleroInstrumentInfo("ISHAR.III PLC CORE MSCI WORLD (AS)", 5, AssetImportMappingRef(1L, 10L)),
                BoleroInstrumentInfo("ISHARES PLC CORE MSC E.M.IM UC (AS)", 3, null),
            ),
            totalRows = 8,
            skippedRows = 92,
        )
        whenever(analyzeBoleroImportUseCase.execute(any())).thenReturn(analysisResult)

        mockMvc.perform(multipart("/import/bolero/analyze").file(emptyXlsx).with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalRows").value(8))
            .andExpect(jsonPath("$.skippedRows").value(92))
            .andExpect(jsonPath("$.instruments.length()").value(2))
            .andExpect(jsonPath("$.instruments[0].externalName").value("ISHAR.III PLC CORE MSCI WORLD (AS)"))
            .andExpect(jsonPath("$.instruments[0].mapping.assetId").value(10))
            .andExpect(jsonPath("$.instruments[1].mapping").doesNotExist())
    }

    @Test
    fun `POST import bolero returns import result`() {
        val importResult = ImportResult(
            imported = 5,
            skipped = 3,
            rows = listOf(
                ImportRowResult(11, ImportStatus.IMPORTED, "ok"),
                ImportRowResult(12, ImportStatus.SKIPPED, "no mapping defined for: UNKNOWN"),
            ),
        )
        whenever(portfolioQueryService.isOwnedBy(1L, 1L)).thenReturn(true)
        whenever(accountService.isOwnedBy(1L, 1L)).thenReturn(true)
        whenever(importBoleroTransactionsUseCase.execute(eq(1L), eq(1L), any(), any())).thenReturn(importResult)

        mockMvc.perform(
            multipart("/portfolios/1/transactions/import/bolero")
                .file(emptyXlsx)
                .param("accountId", "1")
                .with(user(owner))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imported").value(5))
            .andExpect(jsonPath("$.skipped").value(3))
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[0].status").value("IMPORTED"))
            .andExpect(jsonPath("$.rows[1].reason").value("no mapping defined for: UNKNOWN"))
    }

    @Test
    fun `GET asset-mappings returns list`() {
        whenever(assetImportMappingService.listMappings(null))
            .thenReturn(listOf(AssetImportMapping(1L, "bolero", "INSTRUMENT X", 10L)))

        mockMvc.perform(get("/import/asset-mappings").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].broker").value("bolero"))
            .andExpect(jsonPath("$[0].externalName").value("INSTRUMENT X"))
            .andExpect(jsonPath("$[0].assetId").value(10))
    }

    @Test
    fun `POST asset-mappings creates and returns 201`() {
        val created = AssetImportMapping(2L, "bolero", "NEW INSTRUMENT", 20L)
        whenever(createAssetImportMappingUseCase.execute("bolero", "NEW INSTRUMENT", 20L)).thenReturn(created)

        mockMvc.perform(
            post("/import/asset-mappings")
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf(
                    "broker" to "bolero",
                    "externalName" to "NEW INSTRUMENT",
                    "assetId" to 20,
                )))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.broker").value("bolero"))
    }

    @Test
    fun `DELETE asset-mappings returns 204`() {
        mockMvc.perform(delete("/import/asset-mappings/1").with(user(owner)))
            .andExpect(status().isNoContent)
    }
}
