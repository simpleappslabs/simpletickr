package com.simpletickr.transaction

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFailsWith

class TransactionTest {

    private val date = LocalDate.of(2024, 1, 15)

    @Test
    fun `valid transaction is created`() {
        Transaction(1L, 10L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)
    }

    @Test
    fun `zero quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Transaction(1L, 10L, 2L, TransactionType.BUY, BigDecimal.ZERO, BigDecimal("100"), date, null)
        }
    }

    @Test
    fun `negative quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Transaction(1L, 10L, 2L, TransactionType.BUY, BigDecimal("-1"), BigDecimal("100"), date, null)
        }
    }

    @Test
    fun `negative price is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Transaction(1L, 10L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("-0.01"), date, null)
        }
    }

    @Test
    fun `zero price is allowed`() {
        Transaction(1L, 10L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal.ZERO, date, null)
    }

    @Test
    fun `negative fees are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Transaction(1L, 10L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, BigDecimal("-1"))
        }
    }
}
