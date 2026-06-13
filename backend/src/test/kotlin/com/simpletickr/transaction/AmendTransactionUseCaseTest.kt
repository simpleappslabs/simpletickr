package com.simpletickr.transaction

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmendTransactionUseCaseTest {

    private val transactionRepository = mock<TransactionRepository>()
    private val useCase = AmendTransactionUseCase(transactionRepository)

    private val date = LocalDate.of(2024, 1, 15)
    private val existing = Transaction(1L, 10L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)

    private fun amendCommand(
        quantity: BigDecimal = BigDecimal("10"),
        price: BigDecimal = BigDecimal("120"),
    ) = AmendTransactionCommand(
        assetId = existing.assetId,
        type = TransactionType.BUY,
        quantity = quantity,
        price = price,
        date = date,
        fees = null,
    )

    @Test
    fun `execute returns null when transaction not found`() {
        whenever(transactionRepository.findById(99L)).thenReturn(null)

        assertNull(useCase.execute(10L, 99L, amendCommand()))
        verify(transactionRepository, never()).update(any())
    }

    @Test
    fun `execute returns null when transaction belongs to different portfolio`() {
        whenever(transactionRepository.findById(1L)).thenReturn(existing)

        assertNull(useCase.execute(99L, 1L, amendCommand()))
        verify(transactionRepository, never()).update(any())
    }

    @Test
    fun `execute updates and returns amended transaction`() {
        val command = amendCommand(quantity = BigDecimal("10"), price = BigDecimal("120"))
        val amended = existing.copy(quantity = BigDecimal("10"), price = BigDecimal("120"))
        whenever(transactionRepository.findById(1L)).thenReturn(existing)
        whenever(transactionRepository.update(any())).thenReturn(amended)

        val result = useCase.execute(10L, 1L, command)

        assertEquals(amended, result)
    }
}
