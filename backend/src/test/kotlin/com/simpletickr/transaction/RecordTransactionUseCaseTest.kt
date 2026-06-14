package com.simpletickr.transaction

import com.simpletickr.asset.Listing
import com.simpletickr.asset.ListingRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class RecordTransactionUseCaseTest {

    private val transactionRepository = mock<TransactionRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val useCase = RecordTransactionUseCase(transactionRepository, listingRepository)

    private val date = LocalDate.of(2024, 1, 15)
    private val listing = Listing(id = 5L, assetId = 2L, exchange = null, ticker = "AAPL", currency = "USD")

    @Test
    fun `execute saves transaction and returns it with assigned id`() {
        val command = RecordTransactionCommand(
            listingId = 5L,
            type = TransactionType.BUY,
            quantity = BigDecimal("5"),
            price = BigDecimal("100"),
            date = date,
            fees = null,
        )
        val saved = Transaction(42L, 10L, 5L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)
        whenever(listingRepository.findById(5L)).thenReturn(listing)
        whenever(transactionRepository.save(any())).thenReturn(saved)

        val result = useCase.execute(10L, command)

        assertEquals(42L, result.id)
        assertEquals(10L, result.portfolioId)
        assertEquals(5L, result.listingId)
        assertEquals(2L, result.assetId)
    }

    @Test
    fun `execute passes portfolioId from argument, not command`() {
        val command = RecordTransactionCommand(
            listingId = 5L,
            type = TransactionType.BUY,
            quantity = BigDecimal("5"),
            price = BigDecimal("100"),
            date = date,
            fees = null,
        )
        val saved = Transaction(1L, 99L, 5L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)
        whenever(listingRepository.findById(5L)).thenReturn(listing)
        whenever(transactionRepository.save(any())).thenReturn(saved)

        val result = useCase.execute(99L, command)

        assertEquals(99L, result.portfolioId)
    }
}
