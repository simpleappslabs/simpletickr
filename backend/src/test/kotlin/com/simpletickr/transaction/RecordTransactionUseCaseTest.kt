package com.simpletickr.transaction

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class RecordTransactionUseCaseTest {

    private val transactionRepository = mock<TransactionRepository>()
    private val useCase = RecordTransactionUseCase(transactionRepository)

    private val date = LocalDate.of(2024, 1, 15)

    @Test
    fun `execute saves transaction and returns it with assigned id`() {
        val command = RecordTransactionCommand(
            assetId = 2L,
            type = TransactionType.BUY,
            quantity = BigDecimal("5"),
            price = BigDecimal("100"),
            date = date,
            fees = null,
        )
        val saved = Transaction(42L, 10L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)
        whenever(transactionRepository.save(any())).thenReturn(saved)

        val result = useCase.execute(10L, command)

        assertEquals(42L, result.id)
        assertEquals(10L, result.portfolioId)
        assertEquals(2L, result.assetId)
    }

    @Test
    fun `execute passes portfolioId from argument, not command`() {
        val command = RecordTransactionCommand(
            assetId = 2L,
            type = TransactionType.BUY,
            quantity = BigDecimal("5"),
            price = BigDecimal("100"),
            date = date,
            fees = null,
        )
        val saved = Transaction(1L, 99L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)
        whenever(transactionRepository.save(any())).thenReturn(saved)

        val result = useCase.execute(99L, command)

        assertEquals(99L, result.portfolioId)
    }
}
