package com.simpletickr.transaction

import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.fx.model.FxRate
import com.simpletickr.fx.FxRateService
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transaction.usecase.AmendTransactionUseCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val listingRepository = mock<ListingRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val fxRateService = mock<FxRateService>()
    private val userSettingsRepository = mock<UserSettingsRepository>()
    private val useCase = AmendTransactionUseCase(transactionRepository, listingRepository, accountRepository, fxRateService, userSettingsRepository)

    private val date = LocalDate.of(2024, 1, 15)
    private val listing = Listing(id = 5L, assetId = 2L, exchange = null, ticker = "AAPL", currency = CurrencyCode("USD"))
    private val existing = Transaction(1L, 10L, 5L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null, accountId = 1L)

    init {
        whenever(userSettingsRepository.find(1L)).thenReturn(UserSettings(CurrencyCode("EUR")))
        whenever(accountRepository.isOwnedBy(1L, 1L)).thenReturn(true)
        whenever(fxRateService.lookupOrFetch(any(), any(), any())).thenReturn(
            FxRate(CurrencyCode("EUR"), CurrencyCode("USD"), date, BigDecimal("1.08"))
        )
    }

    private fun amendCommand(
        quantity: BigDecimal = BigDecimal("10"),
        price: BigDecimal = BigDecimal("120"),
    ) = AmendTransactionCommand(
        listingId = existing.listingId,
        type = TransactionType.BUY,
        quantity = quantity,
        price = price,
        date = date,
        fees = null,
        accountId = 1L,
    )

    @Test
    fun `execute returns null when transaction not found`() {
        whenever(transactionRepository.findById(99L)).thenReturn(null)

        assertNull(useCase.execute(10L, 99L, amendCommand(), 1L))
        verify(transactionRepository, never()).update(any())
    }

    @Test
    fun `execute returns null when transaction belongs to different portfolio`() {
        whenever(transactionRepository.findById(1L)).thenReturn(existing)

        assertNull(useCase.execute(99L, 1L, amendCommand(), 1L))
        verify(transactionRepository, never()).update(any())
    }

    @Test
    fun `execute updates and returns amended transaction`() {
        val command = amendCommand(quantity = BigDecimal("10"), price = BigDecimal("120"))
        val amended = existing.copy(quantity = BigDecimal("10"), price = BigDecimal("120"))
        whenever(transactionRepository.findById(1L)).thenReturn(existing)
        whenever(listingRepository.findById(5L)).thenReturn(listing)
        whenever(transactionRepository.update(any())).thenReturn(amended)

        val result = useCase.execute(10L, 1L, command, 1L)

        assertEquals(amended, result)
    }

    @Test
    fun `execute throws when transaction is part of a trade`() {
        whenever(transactionRepository.findById(1L)).thenReturn(existing.copy(tradeId = 55L))

        assertThrows<IllegalArgumentException> { useCase.execute(10L, 1L, amendCommand(), 1L) }
        verify(transactionRepository, never()).update(any())
    }

}
