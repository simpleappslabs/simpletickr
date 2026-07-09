package com.simpletickr.brokerimport.bolero

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.brokerimport.AssetImportMapping
import com.simpletickr.brokerimport.AssetImportMappingRepository
import com.simpletickr.brokerimport.ImportStatus
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transaction.usecase.RecordTransactionUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ImportBoleroTransactionsUseCaseTest {

    private val mappingRepository = mock<AssetImportMappingRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val recordTransactionUseCase = mock<RecordTransactionUseCase>()

    private val boleroAccount = Account(id = 1L, name = "Bolero", broker = "Bolero", accountType = AccountType.BROKERAGE, currency = null, accountNumber = null, institution = null)

    private val useCase = ImportBoleroTransactionsUseCase(
        mappingRepository, accountRepository, listingRepository, transactionRepository, recordTransactionUseCase
    )

    private val portfolioId = 1L
    private val assetId = 10L
    private val listingId = 20L
    private val date = LocalDate.of(2025, 3, 15)
    private val instrumentName = "ISHAR.III PLC CORE MSCI WORLD (AS)"

    private fun buildMinimalXlsx(transactionDesc: String, qty: Double = 5.0, price: Double = 100.0): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet()
        for (i in 0..9) sheet.createRow(i)
        val row = sheet.createRow(10)
        val dateCell = row.createCell(1)
        val style = wb.createCellStyle()
        style.dataFormat = wb.creationHelper.createDataFormat().getFormat("yyyy-MM-dd")
        dateCell.cellStyle = style
        dateCell.setCellValue(java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
        row.createCell(5).setCellValue(transactionDesc)
        row.createCell(11).setCellValue("EUR")
        row.createCell(13).setCellValue(qty)
        row.createCell(15).setCellValue(price)
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    private fun multipartFile(bytes: ByteArray) =
        MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ByteArrayInputStream(bytes))

    private fun savedTransaction() = Transaction(
        id = 100L, portfolioId = portfolioId, listingId = listingId, assetId = assetId,
        type = TransactionType.BUY, quantity = BigDecimal("5"), price = BigDecimal("100"),
        date = date, fees = null, accountId = 1L,
    )

    @BeforeEach
    fun setup() {
        whenever(accountRepository.findAll()).thenReturn(listOf(boleroAccount))
        whenever(transactionRepository.existsByExternalId(any(), any())).thenReturn(false)
        whenever(mappingRepository.findByBrokerAndName("bolero", instrumentName))
            .thenReturn(AssetImportMapping(1L, "bolero", instrumentName, assetId))
        whenever(listingRepository.findByAssetId(assetId))
            .thenReturn(listOf(Listing(listingId, assetId, null, "IWDA", CurrencyCode("EUR"))))
        whenever(recordTransactionUseCase.execute(any(), any())).thenReturn(savedTransaction())
    }

    @Test
    fun `recognized and mapped row is imported`() {
        val file = multipartFile(buildMinimalXlsx("Achat Online $instrumentName"))

        val result = useCase.execute(portfolioId, file)

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(ImportStatus.IMPORTED, result.rows[0].status)
        assertEquals("ok", result.rows[0].reason)
    }

    @Test
    fun `deposit row is passed through as skipped`() {
        val file = multipartFile(buildMinimalXlsx("Versement sur compte client"))

        val result = useCase.execute(portfolioId, file)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(ImportStatus.SKIPPED, result.rows[0].status)
        verify(recordTransactionUseCase, never()).execute(any(), any())
    }

    @Test
    fun `unmapped instrument is skipped with descriptive reason`() {
        whenever(mappingRepository.findByBrokerAndName("bolero", instrumentName)).thenReturn(null)
        val file = multipartFile(buildMinimalXlsx("Achat Online $instrumentName"))

        val result = useCase.execute(portfolioId, file)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("no mapping defined for: $instrumentName", result.rows[0].reason)
    }

    @Test
    fun `already imported row is skipped`() {
        whenever(transactionRepository.existsByExternalId(any(), any())).thenReturn(true)
        val file = multipartFile(buildMinimalXlsx("Achat Online $instrumentName"))

        val result = useCase.execute(portfolioId, file)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("already imported", result.rows[0].reason)
        verify(recordTransactionUseCase, never()).execute(any(), any())
    }

    @Test
    fun `no EUR listing for asset skips row`() {
        whenever(listingRepository.findByAssetId(assetId))
            .thenReturn(listOf(Listing(listingId, assetId, null, "IWDA", CurrencyCode("USD"))))
        val file = multipartFile(buildMinimalXlsx("Achat Online $instrumentName"))

        val result = useCase.execute(portfolioId, file)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("no listing in currency EUR for asset $assetId", result.rows[0].reason)
    }

    @Test
    fun `recordTransaction exception skips row with message`() {
        whenever(recordTransactionUseCase.execute(any(), any()))
            .thenThrow(IllegalArgumentException("No FX rate available"))
        val file = multipartFile(buildMinimalXlsx("Achat Online $instrumentName"))

        val result = useCase.execute(portfolioId, file)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("No FX rate available", result.rows[0].reason)
    }
}
