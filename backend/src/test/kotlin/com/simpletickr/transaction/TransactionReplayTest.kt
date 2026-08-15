package com.simpletickr.transaction

import com.simpletickr.transaction.model.TransactionReplay
import com.simpletickr.transaction.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionReplayTest {

    private val d1 = LocalDate.of(2024, 1, 1)
    private val d10 = LocalDate.of(2024, 10, 1)

    private fun assertBd(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")

    @Test
    fun `splitIndex groups ratios by listing id`() {
        val index = TransactionReplay.splitIndex(
            listOf(
                Triple(1L, d1, BigDecimal("2")),
                Triple(2L, d10, BigDecimal("3")),
            ),
        )
        assertEquals(listOf(d1 to BigDecimal("2")), index[1L])
        assertEquals(listOf(d10 to BigDecimal("3")), index[2L])
    }

    @Test
    fun `signedQuantityDelta is positive for BUY, negative for SELL`() {
        val buyDelta = TransactionReplay.signedQuantityDelta(1L, d1, BigDecimal("10"), TransactionType.BUY, emptyMap())
        val sellDelta = TransactionReplay.signedQuantityDelta(1L, d1, BigDecimal("10"), TransactionType.SELL, emptyMap())
        assertBd("10", buyDelta)
        assertBd("-10", sellDelta)
    }

    @Test
    fun `signedQuantityDelta applies the split multiplier for that row's own date`() {
        val splitIndex = mapOf(1L to listOf(d10 to BigDecimal("2")))
        val delta = TransactionReplay.signedQuantityDelta(1L, d1, BigDecimal("10"), TransactionType.BUY, splitIndex)
        assertBd("20", delta)
    }

    @Test
    fun `signedQuantityDelta rejects SPLIT rows`() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionReplay.signedQuantityDelta(1L, d1, BigDecimal("2"), TransactionType.SPLIT, emptyMap())
        }
    }

    @Test
    fun `splitAdjustedQuantity applies compounding splits without a sign`() {
        val splitIndex = mapOf(1L to listOf(d1 to BigDecimal("2"), d10 to BigDecimal("3")))
        val adjusted = TransactionReplay.splitAdjustedQuantity(1L, LocalDate.of(2023, 1, 1), BigDecimal("5"), splitIndex)
        assertBd("30", adjusted)
    }

    @Test
    fun `different rows on the same listing get independently correct adjustment based on their own date`() {
        // Row A (day 1, before both splits): adjusted by 2 x 3 = 6
        // Row B (day 5, between the two splits): adjusted by 3 only
        val splitIndex = mapOf(1L to listOf(LocalDate.of(2024, 3, 1) to BigDecimal("2"), d10 to BigDecimal("3")))
        val rowA = TransactionReplay.splitAdjustedQuantity(1L, d1, BigDecimal("1"), splitIndex)
        val rowB = TransactionReplay.splitAdjustedQuantity(1L, LocalDate.of(2024, 5, 1), BigDecimal("1"), splitIndex)
        assertBd("6", rowA)
        assertBd("3", rowB)
    }
}
