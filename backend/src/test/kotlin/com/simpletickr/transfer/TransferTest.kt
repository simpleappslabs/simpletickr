package com.simpletickr.transfer

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFailsWith
import kotlin.test.Test

class TransferTest {

    private val date = LocalDate.of(2024, 1, 15)

    private fun transfer(quantity: BigDecimal, assetFeeQuantity: BigDecimal? = null, sourceAccountId: Long = 1L, destinationAccountId: Long = 2L) =
        Transfer(
            id = 1L, portfolioId = 10L, listingId = 5L, assetId = 2L,
            quantity = quantity, assetFeeQuantity = assetFeeQuantity, date = date,
            sourceAccountId = sourceAccountId, destinationAccountId = destinationAccountId,
        )

    @Test
    fun `valid transfer is created`() {
        transfer(BigDecimal("5"))
    }

    @Test
    fun `zero quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { transfer(BigDecimal.ZERO) }
    }

    @Test
    fun `negative quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { transfer(BigDecimal("-1")) }
    }

    @Test
    fun `source and destination account must differ`() {
        assertFailsWith<IllegalArgumentException> { transfer(BigDecimal("5"), sourceAccountId = 1L, destinationAccountId = 1L) }
    }

    @Test
    fun `zero asset fee quantity is allowed`() {
        transfer(BigDecimal("5"), assetFeeQuantity = BigDecimal.ZERO)
    }

    @Test
    fun `negative asset fee quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { transfer(BigDecimal("5"), assetFeeQuantity = BigDecimal("-0.01")) }
    }

    @Test
    fun `asset fee quantity equal to quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { transfer(BigDecimal("5"), assetFeeQuantity = BigDecimal("5")) }
    }

    @Test
    fun `asset fee quantity greater than quantity is rejected`() {
        assertFailsWith<IllegalArgumentException> { transfer(BigDecimal("5"), assetFeeQuantity = BigDecimal("6")) }
    }
}
