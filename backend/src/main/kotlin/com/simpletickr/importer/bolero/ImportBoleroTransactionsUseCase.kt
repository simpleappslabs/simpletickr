package com.simpletickr.importer.bolero

import com.simpletickr.importer.AssetImportMappingRepository
import com.simpletickr.importer.BrokerParseResult
import com.simpletickr.importer.ImportResult
import com.simpletickr.importer.ImportRowResult
import com.simpletickr.importer.ImportStatus
import com.simpletickr.asset.ListingRepository
import com.simpletickr.transaction.RecordTransactionCommand
import com.simpletickr.transaction.RecordTransactionUseCase
import com.simpletickr.transaction.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.Base64

@Service
class ImportBoleroTransactionsUseCase(
    private val mappingRepository: AssetImportMappingRepository,
    private val listingRepository: ListingRepository,
    private val transactionRepository: TransactionRepository,
    private val recordTransactionUseCase: RecordTransactionUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(portfolioId: Long, file: MultipartFile): ImportResult {
        log.info("Importing Bolero transactions: portfolioId={}, file={}", portfolioId, file.originalFilename)
        val parseResults = file.inputStream.use { BoleroXlsParser.parse(it) }

        val rows = mutableListOf<ImportRowResult>()
        var imported = 0
        var skipped = 0

        for (result in parseResults) {
            when (result) {
                is BrokerParseResult.Skipped -> {
                    rows.add(ImportRowResult(result.lineNumber, ImportStatus.SKIPPED, result.reason))
                    skipped++
                }
                is BrokerParseResult.Recognized -> {
                    val row = result.row
                    val externalId = computeExternalId(row)

                    val rowResult = tryImport(portfolioId, row, externalId)
                    rows.add(rowResult)
                    if (rowResult.status == ImportStatus.IMPORTED) imported++ else skipped++
                }
            }
        }

        log.info("Bolero import complete: imported={}, skipped={}", imported, skipped)
        return ImportResult(imported, skipped, rows)
    }

    private fun tryImport(portfolioId: Long, row: com.simpletickr.importer.BrokerTransactionRow, externalId: String): ImportRowResult {
        if (transactionRepository.existsByExternalId(portfolioId, externalId)) {
            return ImportRowResult(row.lineNumber, ImportStatus.SKIPPED, "already imported")
        }

        val mapping = mappingRepository.findByBrokerAndName("bolero", row.externalInstrumentName)
            ?: return ImportRowResult(
                row.lineNumber, ImportStatus.SKIPPED,
                "no mapping defined for: ${row.externalInstrumentName}"
            )

        val listings = listingRepository.findByAssetId(mapping.assetId)
        val listing = listings.firstOrNull { it.currency == row.currency }
            ?: return ImportRowResult(
                row.lineNumber, ImportStatus.SKIPPED,
                "no listing in currency ${row.currency.value} for asset ${mapping.assetId}"
            )

        return try {
            recordTransactionUseCase.execute(
                portfolioId,
                RecordTransactionCommand(
                    listingId = listing.id,
                    type = row.type,
                    quantity = row.quantity,
                    price = row.price,
                    date = row.date,
                    fees = null,
                    fxRate = null,
                    externalId = externalId,
                )
            )
            ImportRowResult(row.lineNumber, ImportStatus.IMPORTED, "ok")
        } catch (e: Exception) {
            ImportRowResult(row.lineNumber, ImportStatus.SKIPPED, e.message ?: "unexpected error")
        }
    }

    private fun computeExternalId(row: com.simpletickr.importer.BrokerTransactionRow): String {
        val raw = "bolero|${row.date}|${row.externalInstrumentName}|${row.rawQty}|${row.rawPrice}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        return "bolero:$hash"
    }
}
