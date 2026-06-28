package com.simpletickr.brokerimport

import com.simpletickr.generated.api.ImportApi
import com.simpletickr.generated.model.AssetImportMapping as GeneratedMapping
import com.simpletickr.generated.model.AssetImportMappingRef as GeneratedMappingRef
import com.simpletickr.generated.model.BoleroAnalysisResult as GeneratedAnalysisResult
import com.simpletickr.generated.model.BoleroInstrumentInfo as GeneratedInstrumentInfo
import com.simpletickr.generated.model.CreateAssetImportMappingRequest
import com.simpletickr.generated.model.ImportResult as GeneratedImportResult
import com.simpletickr.generated.model.ImportRowResult as GeneratedRowResult
import com.simpletickr.brokerimport.bolero.AnalyzeBoleroImportUseCase
import com.simpletickr.brokerimport.bolero.ImportBoleroTransactionsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class ImportController(
    private val analyzeBoleroImportUseCase: AnalyzeBoleroImportUseCase,
    private val importBoleroTransactionsUseCase: ImportBoleroTransactionsUseCase,
    private val assetImportMappingService: AssetImportMappingService,
    private val createAssetImportMappingUseCase: CreateAssetImportMappingUseCase,
    private val deleteAssetImportMappingUseCase: DeleteAssetImportMappingUseCase,
) : ImportApi {

    override fun analyzeBoleroImport(file: MultipartFile): ResponseEntity<GeneratedAnalysisResult> {
        val result = analyzeBoleroImportUseCase.execute(file)
        return ResponseEntity.ok(GeneratedAnalysisResult(
            instruments = result.instruments.map { info ->
                GeneratedInstrumentInfo(
                    externalName = info.externalName,
                    rowCount = info.rowCount,
                    mapping = info.mapping?.let { ref ->
                        GeneratedMappingRef(id = ref.id, assetId = ref.assetId)
                    },
                )
            },
            totalRows = result.totalRows,
            skippedRows = result.skippedRows,
        ))
    }

    override fun importBoleroTransactions(portfolioId: Long, file: MultipartFile): ResponseEntity<GeneratedImportResult> {
        val result = importBoleroTransactionsUseCase.execute(portfolioId, file)
        return ResponseEntity.ok(GeneratedImportResult(
            imported = result.imported,
            skipped = result.skipped,
            rows = result.rows.map { row ->
                GeneratedRowResult(
                    line = row.line,
                    status = GeneratedRowResult.Status.valueOf(row.status.name),
                    reason = row.reason,
                )
            },
        ))
    }

    override fun listAssetImportMappings(broker: String?): ResponseEntity<List<GeneratedMapping>> {
        val mappings = assetImportMappingService.listMappings(broker)
        return ResponseEntity.ok(mappings.map { it.toGenerated() })
    }

    override fun createAssetImportMapping(createAssetImportMappingRequest: CreateAssetImportMappingRequest): ResponseEntity<GeneratedMapping> {
        val mapping = createAssetImportMappingUseCase.execute(
            broker = createAssetImportMappingRequest.broker,
            externalName = createAssetImportMappingRequest.externalName,
            assetId = createAssetImportMappingRequest.assetId,
        )
        return ResponseEntity.status(201).body(mapping.toGenerated())
    }

    override fun deleteAssetImportMapping(id: Long): ResponseEntity<Unit> {
        deleteAssetImportMappingUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }

    private fun AssetImportMapping.toGenerated() = GeneratedMapping(
        id = id,
        broker = broker,
        externalName = externalName,
        assetId = assetId,
    )
}
