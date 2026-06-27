package com.simpletickr.transaction

import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transaction.usecase.DeleteTransactionUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteTransactionUseCaseTest {

    private val transactionRepository = mock<TransactionRepository>()
    private val useCase = DeleteTransactionUseCase(transactionRepository)

    private val existing = Transaction(1L, 10L, 5L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), LocalDate.of(2024, 1, 15), null)

    @Test
    fun `execute returns false when transaction not found`() {
        whenever(transactionRepository.findById(99L)).thenReturn(null)

        assertFalse(useCase.execute(10L, 99L))
        verify(transactionRepository, never()).delete(99L)
    }

    @Test
    fun `execute returns false when transaction belongs to different portfolio`() {
        whenever(transactionRepository.findById(1L)).thenReturn(existing)

        assertFalse(useCase.execute(99L, 1L))
        verify(transactionRepository, never()).delete(1L)
    }

    @Test
    fun `execute deletes transaction and returns true`() {
        whenever(transactionRepository.findById(1L)).thenReturn(existing)

        assertTrue(useCase.execute(10L, 1L))
        verify(transactionRepository).delete(1L)
    }
}
