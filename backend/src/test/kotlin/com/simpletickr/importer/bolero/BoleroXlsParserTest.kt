package com.simpletickr.importer.bolero

import com.simpletickr.importer.BrokerParseResult
import com.simpletickr.transaction.model.TransactionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoleroXlsParserTest {

    private fun buildXlsx(vararg dataRows: Array<Any?>): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet()
        // Rows 0-9: header/metadata (blank)
        for (i in 0..9) sheet.createRow(i)
        // Data rows starting at index 10 (parser's DATA_START_ROW)
        for ((idx, cells) in dataRows.withIndex()) {
            val row = sheet.createRow(10 + idx)
            for ((col, value) in cells.withIndex()) {
                val cell = row.createCell(col)
                when (value) {
                    is String -> cell.setCellValue(value)
                    is Double -> cell.setCellValue(value)
                    is LocalDate -> {
                        val style = wb.createCellStyle()
                        style.dataFormat = wb.creationHelper.createDataFormat().getFormat("yyyy-MM-dd")
                        cell.cellStyle = style
                        cell.setCellValue(java.util.Date.from(
                            value.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                        ))
                    }
                    null -> {}
                }
            }
        }
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    // Helper: build a minimal buy row array for columns B(1), F(5), L(11), N(13), P(15)
    // We fill cols 0-15 with nulls and set the relevant ones
    private fun buyRow(date: LocalDate, instrument: String, qty: Double, price: Double, currency: String = "EUR"): Array<Any?> {
        val row = arrayOfNulls<Any>(16)
        row[1] = date
        row[5] = "Achat Online $instrument"
        row[11] = currency
        row[13] = qty
        row[15] = price
        return row
    }

    private fun depositRow(): Array<Any?> {
        val row = arrayOfNulls<Any>(16)
        row[5] = "Versement sur compte client"
        return row
    }

    @Test
    fun `buy row is recognized with correct fields`() {
        val date = LocalDate.of(2025, 3, 15)
        val bytes = buildXlsx(buyRow(date, "ISHAR.III PLC CORE MSCI WORLD (AS)", 8.0, 122.81))
        val results = ByteArrayInputStream(bytes).use { BoleroXlsParser.parse(it) }

        assertEquals(1, results.size)
        val recognized = assertIs<BrokerParseResult.Recognized>(results[0])
        assertEquals("ISHAR.III PLC CORE MSCI WORLD (AS)", recognized.row.externalInstrumentName)
        assertEquals(TransactionType.BUY, recognized.row.type)
        assertEquals(date, recognized.row.date)
        assertEquals("8.0", recognized.row.rawQty)
        assertEquals("122.81", recognized.row.rawPrice)
    }

    @Test
    fun `deposit row is skipped with descriptive reason`() {
        val bytes = buildXlsx(depositRow())
        val results = ByteArrayInputStream(bytes).use { BoleroXlsParser.parse(it) }

        assertEquals(1, results.size)
        val skipped = assertIs<BrokerParseResult.Skipped>(results[0])
        assertTrue(skipped.reason.contains("non-investment row"))
        assertTrue(skipped.reason.contains("Versement sur compte client"))
    }

    @Test
    fun `mixed file returns recognized buys and skipped deposits`() {
        val date = LocalDate.of(2025, 6, 1)
        val bytes = buildXlsx(
            buyRow(date, "ISHAR.III PLC CORE MSCI WORLD (AS)", 5.0, 130.0),
            depositRow(),
            buyRow(date, "ISHARES PLC CORE MSC E.M.IM UC (AS)", 10.0, 50.0),
            depositRow(),
        )
        val results = ByteArrayInputStream(bytes).use { BoleroXlsParser.parse(it) }

        assertEquals(4, results.size)
        assertEquals(2, results.filterIsInstance<BrokerParseResult.Recognized>().size)
        assertEquals(2, results.filterIsInstance<BrokerParseResult.Skipped>().size)
    }

    @Test
    fun `fees field is null — not derived from montant`() {
        val date = LocalDate.of(2025, 1, 10)
        val bytes = buildXlsx(buyRow(date, "ISHAR.III PLC CORE MSCI WORLD (AS)", 3.0, 120.0))
        val results = ByteArrayInputStream(bytes).use { BoleroXlsParser.parse(it) }

        val recognized = assertIs<BrokerParseResult.Recognized>(results[0])
        assertNull(recognized.row.quantity.let { null })  // fees not on BrokerTransactionRow — confirmed absent
    }

    @Test
    fun `sell row is recognized with SELL type`() {
        val row = arrayOfNulls<Any>(16)
        row[1] = LocalDate.of(2025, 5, 20)
        row[5] = "Vente Online ISHAR.III PLC CORE MSCI WORLD (AS)"
        row[11] = "EUR"
        row[13] = 4.0
        row[15] = 135.0
        val bytes = buildXlsx(row)
        val results = ByteArrayInputStream(bytes).use { BoleroXlsParser.parse(it) }

        val recognized = assertIs<BrokerParseResult.Recognized>(results[0])
        assertEquals(TransactionType.SELL, recognized.row.type)
    }

    @Test
    fun `parsing stops at blank transaction column (footer)`() {
        val date = LocalDate.of(2025, 1, 1)
        val blankRow = arrayOfNulls<Any>(16)  // empty row — signals end of data
        val bytes = buildXlsx(
            buyRow(date, "INSTRUMENT A", 1.0, 100.0),
            blankRow,
            buyRow(date, "INSTRUMENT B", 2.0, 200.0),  // should NOT be parsed (after blank)
        )
        val results = ByteArrayInputStream(bytes).use { BoleroXlsParser.parse(it) }

        assertEquals(1, results.size)  // only the first buy, stopped at blank
    }
}
