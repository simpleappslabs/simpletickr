package com.simpletickr.portfolio

import com.simpletickr.asset.model.Asset
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.gains.RealizationMethod
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transfer.Transfer
import com.simpletickr.transfer.TransferRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class RealizedGainsServiceTest {

    private val transactionRepository = mock<TransactionRepository>()
    private val transferRepository = mock<TransferRepository>()
    private val assetRepository = mock<AssetRepository>()

    private val service = RealizedGainsService(transactionRepository, transferRepository, assetRepository)

    private val from = LocalDate.of(2024, 1, 1)
    private val to = LocalDate.of(2024, 12, 31)
    private val listing = Listing(id = 10L, assetId = 1L, exchange = null, ticker = "AAPL", currency = CurrencyCode("USD"))
    private val asset = Asset(id = 1L, uuid = UUID.randomUUID(), isin = null, name = "Apple", type = AssetType.STOCK, listings = listOf(listing))

    private fun transaction(id: Long, type: TransactionType, qty: String, price: String, date: String) = Transaction(
        id = id, portfolioId = 5L, listingId = 10L, assetId = 1L,
        type = type, quantity = BigDecimal(qty), price = BigDecimal(price),
        date = LocalDate.parse(date), fees = null, accountId = 1L,
    )

    @Test
    fun `computes report from transactions, listing map and positive transfer fees only`() {
        val txs = listOf(
            transaction(1, TransactionType.BUY, "10", "100", "2024-01-01"),
            transaction(2, TransactionType.SELL, "5", "150", "2024-06-01"),
        )
        whenever(transactionRepository.findAllForPortfolio(5L)).thenReturn(txs)
        whenever(transferRepository.findAllForPortfolio(5L)).thenReturn(listOf(
            Transfer(id = 1L, portfolioId = 5L, listingId = 10L, assetId = 1L, quantity = BigDecimal("1"), assetFeeQuantity = BigDecimal("0.1"), date = LocalDate.of(2024, 3, 1), sourceAccountId = 1L, destinationAccountId = 2L),
            Transfer(id = 2L, portfolioId = 5L, listingId = 10L, assetId = 1L, quantity = BigDecimal("1"), assetFeeQuantity = null, date = LocalDate.of(2024, 4, 1), sourceAccountId = 1L, destinationAccountId = 2L),
        ))
        whenever(assetRepository.findAll()).thenReturn(listOf(asset))

        val report = service.getRealizedGains(5L, RealizationMethod.FIFO, from, to)

        assertEquals(1, report.entries.size)
        assertEquals(RealizationMethod.FIFO, report.method)
        assertEquals(from, report.from)
        assertEquals(to, report.to)
    }

    @Test
    fun `returns empty report when there is no activity`() {
        whenever(transactionRepository.findAllForPortfolio(5L)).thenReturn(emptyList())
        whenever(transferRepository.findAllForPortfolio(5L)).thenReturn(emptyList())
        whenever(assetRepository.findAll()).thenReturn(emptyList())

        val report = service.getRealizedGains(5L, RealizationMethod.AVERAGE_COST, from, to)

        assertEquals(0, report.entries.size)
        assertEquals(0, report.byCurrency.size)
    }
}
