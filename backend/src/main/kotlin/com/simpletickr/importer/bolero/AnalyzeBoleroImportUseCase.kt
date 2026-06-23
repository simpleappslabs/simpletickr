package com.simpletickr.importer.bolero

import com.simpletickr.importer.AssetImportMappingRepository
import com.simpletickr.importer.BrokerParseResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

data class BoleroAnalysisResult(
    val instruments: List<BoleroInstrumentInfo>,
    val totalRows: Int,
    val skippedRows: Int,
)

data class BoleroInstrumentInfo(
    val externalName: String,
    val rowCount: Int,
    val mapping: AssetImportMappingRef?,
)

data class AssetImportMappingRef(
    val id: Long,
    val assetId: Long,
)

@Service
class AnalyzeBoleroImportUseCase(
    private val mappingRepository: AssetImportMappingRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(file: MultipartFile): BoleroAnalysisResult {
        log.info("Analyzing Bolero import file: {}", file.originalFilename)
        val parseResults = file.inputStream.use { BoleroXlsParser.parse(it) }

        val recognized = parseResults.filterIsInstance<BrokerParseResult.Recognized>()
        val skipped = parseResults.filterIsInstance<BrokerParseResult.Skipped>()

        val instruments = recognized
            .groupBy { it.row.externalInstrumentName }
            .map { (name, rows) ->
                val existing = mappingRepository.findByBrokerAndName("bolero", name)
                BoleroInstrumentInfo(
                    externalName = name,
                    rowCount = rows.size,
                    mapping = existing?.let { AssetImportMappingRef(it.id, it.assetId) },
                )
            }
            .sortedBy { it.externalName }

        return BoleroAnalysisResult(
            instruments = instruments,
            totalRows = recognized.size,
            skippedRows = skipped.size,
        )
    }
}
