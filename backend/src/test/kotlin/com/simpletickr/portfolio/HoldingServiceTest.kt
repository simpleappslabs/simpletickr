package com.simpletickr.portfolio

import com.simpletickr.portfolio.model.Holding
import com.simpletickr.portfolio.persistence.HoldingRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class HoldingServiceTest {

    private val repo = mock<HoldingRepository>()
    private val service = HoldingService(repo)

    private val usd = CurrencyCode("USD")

    private fun row(
        id: Long,
        type: TransactionType,
        qty: String,
        price: String,
        date: LocalDate,
        listingId: Long = 1L,
        assetId: Long = 1L,
        accountId: Long = 1L,
    ) = HoldingRepository.TransactionRow(
        transactionId = id,
        assetId = assetId,
        assetName = "ACME",
        listingId = listingId,
        exchange = null,
        ticker = "ACME",
        currency = usd,
        type = type,
        quantity = BigDecimal(qty),
        price = BigDecimal(price),
        fees = null,
        fxRate = null,
        date = date,
        accountId = accountId,
    )

    @Test
    fun `no splits - basic WAC holding`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "10", LocalDate.of(2024, 1, 1)),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)

        val holdings = service.getHoldings(1L)

        assertEquals(1, holdings.size)
        assertEquals(BigDecimal("100"), holdings[0].quantity)
        assertBd("10", holdings[0].avgCostLocal)
    }

    @Test
    fun `2-for-1 split doubles quantity and halves average cost`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "20", LocalDate.of(2024, 1, 1)),
            row(2, TransactionType.SPLIT, "2", "0", LocalDate.of(2024, 6, 1)),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)

        val holdings = service.getHoldings(1L)

        assertEquals(1, holdings.size)
        assertEquals(BigDecimal("200"), holdings[0].quantity)
        assertBd("10", holdings[0].avgCostLocal)
    }

    @Test
    fun `buy before split and buy after split - correct WAC`() {
        // BUY 100 @ $20 → after 2:1 split becomes 200 @ $10
        // BUY 50 @ $12 after split (already at post-split price)
        // net qty = 250, total cost = 200*10 + 50*12 = 2000+600 = 2600, avg = 2600/250 = 10.40
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "20", LocalDate.of(2024, 1, 1)),
            row(2, TransactionType.SPLIT, "2", "0", LocalDate.of(2024, 6, 1)),
            row(3, TransactionType.BUY, "50", "12", LocalDate.of(2024, 9, 1)),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)

        val holdings = service.getHoldings(1L)

        assertEquals(1, holdings.size)
        assertBd("250", holdings[0].quantity)
        assertBd("10.4", holdings[0].avgCostLocal)
    }

    @Test
    fun `two sequential splits compound correctly`() {
        // BUY 100 @ $60 → 2:1 split → 3:1 split → 600 shares @ $10
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "60", LocalDate.of(2024, 1, 1)),
            row(2, TransactionType.SPLIT, "2", "0", LocalDate.of(2024, 4, 1)),
            row(3, TransactionType.SPLIT, "3", "0", LocalDate.of(2024, 8, 1)),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)

        val holdings = service.getHoldings(1L)

        assertEquals(1, holdings.size)
        assertBd("600", holdings[0].quantity)
        assertBd("10", holdings[0].avgCostLocal)
    }

    @Test
    fun `sell after split uses adjusted quantities`() {
        // BUY 100 @ $20, SPLIT 2:1 → 200 @ $10, SELL 50 @ $12 → net = 150
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "20", LocalDate.of(2024, 1, 1)),
            row(2, TransactionType.SPLIT, "2", "0", LocalDate.of(2024, 6, 1)),
            row(3, TransactionType.SELL, "50", "12", LocalDate.of(2024, 9, 1)),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)

        val holdings = service.getHoldings(1L)

        assertEquals(1, holdings.size)
        assertBd("150", holdings[0].quantity)
    }

    @Test
    fun `split does not appear as a holding itself`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "10", LocalDate.of(2024, 1, 1)),
            row(2, TransactionType.SPLIT, "2", "0", LocalDate.of(2024, 6, 1)),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)

        val holdings = service.getHoldings(1L)

        assertEquals(1, holdings.size)
    }

    private fun feeRow(listingId: Long, assetId: Long, date: LocalDate, feeQty: String) =
        HoldingRepository.TransferFeeRow(listingId = listingId, assetId = assetId, date = date, feeQuantity = BigDecimal(feeQty))

    @Test
    fun `transfer fee reduces quantity but not average cost - a transfer moves custody, not inventory`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "10", "100", LocalDate.of(2024, 1, 1)),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)
        whenever(repo.findTransferFeeRows(1L)).thenReturn(listOf(feeRow(1L, 1L, LocalDate.of(2024, 3, 1), "0.5")))

        val holdings = service.getHoldings(1L)

        assertEquals(1, holdings.size)
        assertBd("9.5", holdings[0].quantity)
        assertBd("100", holdings[0].avgCostLocal)
    }

    @Test
    fun `asOf is forwarded to the repository for both transactions and transfer fees`() {
        val asOf = LocalDate.of(2024, 3, 1)
        whenever(repo.findTransactionRows(1L, asOf)).thenReturn(emptyList())
        whenever(repo.findTransferFeeRows(1L, asOf)).thenReturn(emptyList())

        service.getHoldings(1L, asOf = asOf)

        verify(repo).findTransactionRows(1L, asOf)
        verify(repo).findTransferFeeRows(1L, asOf)
    }

    private fun assertBd(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "Expected $expected but got $actual")

    // ── getHoldingsByAccount ───────────────────────────────────────────────────

    private fun transferRow(
        listingId: Long = 1L,
        assetId: Long = 1L,
        date: LocalDate,
        quantity: String,
        feeQuantity: String? = null,
        sourceAccountId: Long,
        destinationAccountId: Long,
    ) = HoldingRepository.TransferRow(
        listingId = listingId,
        assetId = assetId,
        currency = usd,
        date = date,
        quantity = BigDecimal(quantity),
        feeQuantity = feeQuantity?.let { BigDecimal(it) },
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
    )

    @Test
    fun `getHoldingsByAccount - buys in different accounts stay separate`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "10", LocalDate.of(2024, 1, 1), accountId = 1L),
            row(2, TransactionType.BUY, "50", "20", LocalDate.of(2024, 1, 2), accountId = 2L),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)
        whenever(repo.findTransferRows(1L)).thenReturn(emptyList())

        val holdings = service.getHoldingsByAccount(1L)

        assertEquals(2, holdings.size)
        assertBd("100", holdings.first { it.accountId == 1L }.quantity)
        assertBd("50", holdings.first { it.accountId == 2L }.quantity)
    }

    @Test
    fun `getHoldingsByAccount - transfer moves quantity from source to destination account`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "10", LocalDate.of(2024, 1, 1), accountId = 1L),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)
        whenever(repo.findTransferRows(1L)).thenReturn(listOf(
            transferRow(date = LocalDate.of(2024, 2, 1), quantity = "40", sourceAccountId = 1L, destinationAccountId = 2L),
        ))

        val holdings = service.getHoldingsByAccount(1L)

        assertEquals(2, holdings.size)
        assertBd("60", holdings.first { it.accountId == 1L }.quantity)
        assertBd("40", holdings.first { it.accountId == 2L }.quantity)
    }

    @Test
    fun `getHoldingsByAccount - transfer fee is lost in transit, not credited to destination`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "10", LocalDate.of(2024, 1, 1), accountId = 1L),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)
        whenever(repo.findTransferRows(1L)).thenReturn(listOf(
            transferRow(date = LocalDate.of(2024, 2, 1), quantity = "40", feeQuantity = "1", sourceAccountId = 1L, destinationAccountId = 2L),
        ))

        val holdings = service.getHoldingsByAccount(1L)

        assertBd("60", holdings.first { it.accountId == 1L }.quantity)
        assertBd("39", holdings.first { it.accountId == 2L }.quantity)
    }

    @Test
    fun `getHoldingsByAccount - account fully emptied by transfer is excluded`() {
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "10", LocalDate.of(2024, 1, 1), accountId = 1L),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)
        whenever(repo.findTransferRows(1L)).thenReturn(listOf(
            transferRow(date = LocalDate.of(2024, 2, 1), quantity = "100", sourceAccountId = 1L, destinationAccountId = 2L),
        ))

        val holdings = service.getHoldingsByAccount(1L)

        assertEquals(1, holdings.size)
        assertEquals(2L, holdings[0].accountId)
        assertBd("100", holdings[0].quantity)
    }

    @Test
    fun `getHoldingsByAccount - split applies to both a pre-split buy and a pre-split transfer`() {
        // BUY 100 @ $10 in account 1, transfer 50 (pre-split) to account 2, then 2:1 split.
        // Account 1 ends up with (100-50)*2 = 100, account 2 with 50*2 = 100.
        val rows = listOf(
            row(1, TransactionType.BUY, "100", "10", LocalDate.of(2024, 1, 1), accountId = 1L),
            row(2, TransactionType.SPLIT, "2", "0", LocalDate.of(2024, 3, 1), accountId = 1L),
        )
        whenever(repo.findTransactionRows(1L)).thenReturn(rows)
        whenever(repo.findTransferRows(1L)).thenReturn(listOf(
            transferRow(date = LocalDate.of(2024, 2, 1), quantity = "50", sourceAccountId = 1L, destinationAccountId = 2L),
        ))

        val holdings = service.getHoldingsByAccount(1L)

        assertEquals(2, holdings.size)
        assertBd("100", holdings.first { it.accountId == 1L }.quantity)
        assertBd("100", holdings.first { it.accountId == 2L }.quantity)
    }
}
