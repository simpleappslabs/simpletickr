package com.simpletickr.brokerimport.bolero

import com.simpletickr.brokerimport.BrokerParseResult
import com.simpletickr.brokerimport.BrokerTransactionRow
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.TransactionType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.math.BigDecimal

object BoleroXlsParser {

    // Bolero exports have metadata in rows 1-9, headers in row 9, a gap in row 10, data from row 11.
    // All indices are 0-based in Apache POI.
    private const val DATA_START_ROW = 10  // row 11 (0-based)

    // 0-based column indices (A=0, B=1, …, F=5, J=9, L=11, N=13, P=15)
    private const val COL_DATE = 1         // B: Date de la transaction
    private const val COL_TRANSACTION = 5  // F: Transaction description
    private const val COL_CURRENCY = 11    // L: Devise
    private const val COL_QUANTITY = 13    // N: Quantité
    private const val COL_PRICE = 15       // P: Prix

    fun parse(inputStream: InputStream): List<BrokerParseResult> {
        val results = mutableListOf<BrokerParseResult>()
        XSSFWorkbook(inputStream).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            for (rowIndex in DATA_START_ROW..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: break
                val lineNumber = rowIndex + 1  // 1-based for user-facing messages

                val transactionDesc = row.getCell(COL_TRANSACTION)?.stringCellValue?.trim().orEmpty()
                if (transactionDesc.isEmpty()) break  // reached the footer

                val type = when {
                    transactionDesc.startsWith("Achat Online ") -> TransactionType.BUY
                    transactionDesc.startsWith("Vente Online ") -> TransactionType.SELL
                    else -> {
                        results.add(BrokerParseResult.Skipped(lineNumber, "non-investment row: $transactionDesc"))
                        continue
                    }
                }

                val externalInstrumentName = transactionDesc
                    .removePrefix("Achat Online ")
                    .removePrefix("Vente Online ")

                try {
                    val dateCell = row.getCell(COL_DATE) ?: error("missing date cell")
                    val date = if (DateUtil.isCellDateFormatted(dateCell))
                        dateCell.localDateTimeCellValue.toLocalDate()
                    else
                        error("date cell is not date-formatted")

                    val quantityCell = row.getCell(COL_QUANTITY) ?: error("missing quantity cell")
                    val rawQty = quantityCell.numericCellValue
                    val quantity = BigDecimal(rawQty.toString())

                    val priceCell = row.getCell(COL_PRICE) ?: error("missing price cell")
                    val rawPrice = priceCell.numericCellValue
                    val price = BigDecimal(rawPrice.toString())

                    val currency = CurrencyCode(
                        row.getCell(COL_CURRENCY)?.stringCellValue?.trim() ?: "EUR"
                    )

                    results.add(BrokerParseResult.Recognized(BrokerTransactionRow(
                        lineNumber = lineNumber,
                        externalInstrumentName = externalInstrumentName,
                        type = type,
                        date = date,
                        quantity = quantity,
                        price = price,
                        rawQty = rawQty.toString(),
                        rawPrice = rawPrice.toString(),
                        currency = currency,
                    )))
                } catch (e: Exception) {
                    results.add(BrokerParseResult.Skipped(lineNumber, "parse error: ${e.message}"))
                }
            }
        }
        return results
    }
}
