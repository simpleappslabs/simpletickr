package com.simpletickr.transaction

import com.simpletickr.portfolio.PortfolioQueryService
import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionFilter
import com.simpletickr.transaction.persistence.TransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class TransactionQueryServiceTest {

    private val transactionRepository = mock<TransactionRepository>()
    private val portfolioQueryService = mock<PortfolioQueryService>()

    private val service = TransactionQueryService(transactionRepository, portfolioQueryService)

    private val sample = Transaction(
        id = 1L, portfolioId = 10L, listingId = 5L, assetId = 2L,
        type = TransactionType.BUY,
        quantity = BigDecimal("5"), price = BigDecimal("100"),
        date = LocalDate.of(2024, 1, 15), fees = null, accountId = 1L,
    )

    @Test
    fun `listTransactions returns null when portfolioId is not owned by the user`() {
        val filter = TransactionFilter(portfolioId = 10L)
        whenever(portfolioQueryService.isOwnedBy(10L, 1L)).thenReturn(false)

        val result = service.listTransactions(filter, 0, 25, 1L)

        assertNull(result)
    }

    @Test
    fun `listTransactions queries directly by portfolioId when owned`() {
        val filter = TransactionFilter(portfolioId = 10L)
        whenever(portfolioQueryService.isOwnedBy(10L, 1L)).thenReturn(true)
        whenever(transactionRepository.findAll(filter, 0, 25)).thenReturn(listOf(sample))
        whenever(transactionRepository.count(filter)).thenReturn(1L)

        val result = service.listTransactions(filter, 0, 25, 1L)

        assertEquals(TransactionPageResult(listOf(sample), 1L), result)
    }

    @Test
    fun `listTransactions resolves portfolioIds to all portfolios owned by the user when portfolioId is absent`() {
        val filter = TransactionFilter(type = TransactionType.BUY)
        whenever(portfolioQueryService.listPortfolios(1L)).thenReturn(listOf(
            Portfolio(10L, UUID(0, 10), "Main", 1L),
            Portfolio(11L, UUID(0, 11), "Savings", 1L),
        ))
        val resolvedFilter = filter.copy(portfolioIds = setOf(10L, 11L))
        whenever(transactionRepository.findAll(resolvedFilter, 0, 25)).thenReturn(listOf(sample))
        whenever(transactionRepository.count(resolvedFilter)).thenReturn(1L)

        val result = service.listTransactions(filter, 0, 25, 1L)

        assertEquals(TransactionPageResult(listOf(sample), 1L), result)
    }

    @Test
    fun `getTransaction returns null when the owning portfolio does not belong to the user`() {
        whenever(transactionRepository.findById(1L)).thenReturn(sample)
        whenever(portfolioQueryService.isOwnedBy(10L, 2L)).thenReturn(false)

        assertNull(service.getTransaction(1L, 2L))
    }

    @Test
    fun `getTransaction returns null when not found`() {
        whenever(transactionRepository.findById(99L)).thenReturn(null)

        assertNull(service.getTransaction(99L, 1L))
    }

    @Test
    fun `getTransaction returns the transaction when owned`() {
        whenever(transactionRepository.findById(1L)).thenReturn(sample)
        whenever(portfolioQueryService.isOwnedBy(10L, 1L)).thenReturn(true)

        assertEquals(sample, service.getTransaction(1L, 1L))
    }
}
