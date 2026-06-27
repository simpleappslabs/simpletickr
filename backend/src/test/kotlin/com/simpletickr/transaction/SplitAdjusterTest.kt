package com.simpletickr.transaction

import com.simpletickr.transaction.model.SplitAdjuster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SplitAdjusterTest {

    private val d1 = LocalDate.of(2024, 1, 1)
    private val d5 = LocalDate.of(2024, 5, 1)
    private val d10 = LocalDate.of(2024, 10, 1)

    @Test
    fun `no splits returns multiplier of one`() {
        val adj = SplitAdjuster.adjustmentFor(1L, d1, emptyMap())
        assertEquals(BigDecimal.ONE, adj.multiplier)
    }

    @Test
    fun `split after txDate applies multiplier`() {
        val index = mapOf(1L to listOf(d10 to BigDecimal("2")))
        val adj = SplitAdjuster.adjustmentFor(1L, d1, index)
        assertEquals(BigDecimal("2"), adj.multiplier)
    }

    @Test
    fun `split on same date as txDate does not apply`() {
        val index = mapOf(1L to listOf(d1 to BigDecimal("2")))
        val adj = SplitAdjuster.adjustmentFor(1L, d1, index)
        assertEquals(BigDecimal.ONE, adj.multiplier)
    }

    @Test
    fun `split before txDate does not apply`() {
        val index = mapOf(1L to listOf(d1 to BigDecimal("2")))
        val adj = SplitAdjuster.adjustmentFor(1L, d5, index)
        assertEquals(BigDecimal.ONE, adj.multiplier)
    }

    @Test
    fun `two sequential splits compound`() {
        val index = mapOf(1L to listOf(d5 to BigDecimal("2"), d10 to BigDecimal("3")))
        val adj = SplitAdjuster.adjustmentFor(1L, d1, index)
        assertEquals(BigDecimal("6"), adj.multiplier)
    }

    @Test
    fun `only later split applies when tx is between splits`() {
        val index = mapOf(1L to listOf(d1 to BigDecimal("2"), d10 to BigDecimal("3")))
        val adj = SplitAdjuster.adjustmentFor(1L, d5, index)
        assertEquals(BigDecimal("3"), adj.multiplier)
    }

    @Test
    fun `reverse split ratio less than one`() {
        val index = mapOf(1L to listOf(d10 to BigDecimal("0.5")))
        val adj = SplitAdjuster.adjustmentFor(1L, d1, index)
        assertEquals(BigDecimal("0.5"), adj.multiplier)
    }

    @Test
    fun `unknown listing id returns multiplier of one`() {
        val index = mapOf(99L to listOf(d10 to BigDecimal("2")))
        val adj = SplitAdjuster.adjustmentFor(1L, d1, index)
        assertEquals(BigDecimal.ONE, adj.multiplier)
    }
}
