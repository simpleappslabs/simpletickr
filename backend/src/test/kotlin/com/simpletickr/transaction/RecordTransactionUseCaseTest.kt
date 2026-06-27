package com.simpletickr.transaction

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
import com.simpletickr.transaction.usecase.RecordTransactionUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class RecordTransactionUseCaseTest {

    private val transactionRepository = mock<TransactionRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val fxRateService = mock<FxRateService>()
    private val userSettingsRepository = mock<UserSettingsRepository>()
    private val useCase = RecordTransactionUseCase(transactionRepository, listingRepository, fxRateService, userSettingsRepository)

    private val date = LocalDate.of(2024, 1, 15)

    init {
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(CurrencyCode("EUR")))
        whenever(fxRateService.lookupOrFetch(any(), any(), any())).thenReturn(
            FxRate(CurrencyCode("EUR"), CurrencyCode("USD"), date, BigDecimal("1.08"))
        )
    }
    private val listing = Listing(id = 5L, assetId = 2L, exchange = null, ticker = "AAPL", currency = CurrencyCode("USD"))

    @Test
    fun `execute saves transaction and returns it with assigned id`() {
        val command = RecordTransactionCommand(
            listingId = 5L,
            type = TransactionType.BUY,
            quantity = BigDecimal("5"),
            price = BigDecimal("100"),
            date = date,
            fees = null,
        )
        val saved = Transaction(42L, 10L, 5L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)
        whenever(listingRepository.findById(5L)).thenReturn(listing)
        whenever(transactionRepository.save(any())).thenReturn(saved)

        val result = useCase.execute(10L, command)

        assertEquals(42L, result.id)
        assertEquals(10L, result.portfolioId)
        assertEquals(5L, result.listingId)
        assertEquals(2L, result.assetId)
    }

    @Test
    fun `execute passes portfolioId from argument, not command`() {
        val command = RecordTransactionCommand(
            listingId = 5L,
            type = TransactionType.BUY,
            quantity = BigDecimal("5"),
            price = BigDecimal("100"),
            date = date,
            fees = null,
        )
        val saved = Transaction(1L, 99L, 5L, 2L, TransactionType.BUY, BigDecimal("5"), BigDecimal("100"), date, null)
        whenever(listingRepository.findById(5L)).thenReturn(listing)
        whenever(transactionRepository.save(any())).thenReturn(saved)

        val result = useCase.execute(99L, command)

        assertEquals(99L, result.portfolioId)
    }

    @Test
    fun `SPLIT skips FX rate lookup even for foreign-currency listing`() {
        val foreignListing = listing.copy(currency = CurrencyCode("USD")) // base is EUR in init
        val command = RecordTransactionCommand(
            listingId = 5L,
            type = TransactionType.SPLIT,
            quantity = BigDecimal("2"),
            price = BigDecimal.ZERO,
            date = date,
            fees = null,
        )
        val saved = Transaction(1L, 10L, 5L, 2L, TransactionType.SPLIT, BigDecimal("2"), BigDecimal.ZERO, date, null)
        whenever(listingRepository.findById(5L)).thenReturn(foreignListing)
        whenever(transactionRepository.save(any())).thenReturn(saved)

        val result = useCase.execute(10L, command)

        assertEquals(TransactionType.SPLIT, result.type)
        org.mockito.kotlin.verify(fxRateService, org.mockito.kotlin.never()).lookupOrFetch(any(), any(), any())
    }
}
