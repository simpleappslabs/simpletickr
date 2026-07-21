package com.simpletickr.transfer

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteTransferUseCaseTest {

    private val transferRepository = mock<TransferRepository>()
    private val useCase = DeleteTransferUseCase(transferRepository)

    private val existing = Transfer(
        id = 1L, portfolioId = 10L, listingId = 5L, assetId = 2L,
        quantity = BigDecimal("1.0"), date = LocalDate.of(2024, 1, 15),
        sourceAccountId = 1L, destinationAccountId = 2L,
    )

    @Test
    fun `execute returns false when transfer not found`() {
        whenever(transferRepository.findById(99L)).thenReturn(null)

        assertFalse(useCase.execute(10L, 99L))
        verify(transferRepository, never()).delete(99L)
    }

    @Test
    fun `execute returns false when transfer belongs to different portfolio`() {
        whenever(transferRepository.findById(1L)).thenReturn(existing)

        assertFalse(useCase.execute(99L, 1L))
        verify(transferRepository, never()).delete(1L)
    }

    @Test
    fun `execute deletes transfer and returns true`() {
        whenever(transferRepository.findById(1L)).thenReturn(existing)

        assertTrue(useCase.execute(10L, 1L))
        verify(transferRepository).delete(1L)
    }
}
