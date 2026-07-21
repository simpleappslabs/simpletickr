package com.simpletickr.transaction

import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFailsWith

class TransactionTest {

    private val date = LocalDate.of(2024, 1, 15)

    private fun tx(qty: BigDecimal, price: BigDecimal, fees: BigDecimal? = null) =
        Transaction(1L, 10L, 5L, 2L, TransactionType.BUY, qty, price, date, fees, accountId = 1L)

    @Test
    fun `valid transaction is created`() {
        tx(BigDecimal("5"), BigDecimal("100"))
    }

    @Test
    fun `zero quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { tx(BigDecimal.ZERO, BigDecimal("100")) }
    }

    @Test
    fun `negative quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { tx(BigDecimal("-1"), BigDecimal("100")) }
    }

    @Test
    fun `negative price is rejected`() {
        assertFailsWith<IllegalArgumentException> { tx(BigDecimal("5"), BigDecimal("-0.01")) }
    }

    @Test
    fun `zero price is allowed`() {
        tx(BigDecimal("5"), BigDecimal.ZERO)
    }

    @Test
    fun `negative fees are rejected`() {
        assertFailsWith<IllegalArgumentException> { tx(BigDecimal("5"), BigDecimal("100"), BigDecimal("-1")) }
    }

    private fun txWithAssetFee(quantity: BigDecimal, assetFeeQuantity: BigDecimal) = Transaction(
        1L, 10L, 5L, 2L, TransactionType.TRANSFER_OUT, quantity, BigDecimal("100"), date, null,
        accountId = 1L, assetFeeQuantity = assetFeeQuantity,
    )

    @Test
    fun `zero asset fee quantity is allowed`() {
        txWithAssetFee(BigDecimal("5"), BigDecimal.ZERO)
    }

    @Test
    fun `negative asset fee quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { txWithAssetFee(BigDecimal("5"), BigDecimal("-0.01")) }
    }

    @Test
    fun `asset fee quantity equal to quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { txWithAssetFee(BigDecimal("5"), BigDecimal("5")) }
    }

    @Test
    fun `asset fee quantity greater than quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { txWithAssetFee(BigDecimal("5"), BigDecimal("6")) }
    }
}
