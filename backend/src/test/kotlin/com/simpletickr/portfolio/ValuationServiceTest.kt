package com.simpletickr.portfolio

import com.simpletickr.fx.persistence.FxRateRepository
import com.simpletickr.portfolio.model.AccountHolding
import com.simpletickr.price.model.PricePoint
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class ValuationServiceTest {

    private val holdingService = mock<HoldingService>()
    private val priceHistoryRepository = mock<AssetPriceHistoryRepository>()
    private val fxRateRepository = mock<FxRateRepository>()
    private val userSettingsRepository = mock<UserSettingsRepository>()
    private val service = ValuationService(holdingService, priceHistoryRepository, fxRateRepository, userSettingsRepository)

    private val eur = CurrencyCode("EUR")
    private val usd = CurrencyCode("USD")

    private fun accountHolding(accountId: Long, listingId: Long, currency: CurrencyCode, qty: String) =
        AccountHolding(accountId = accountId, listingId = listingId, currency = currency, quantity = BigDecimal(qty))

    private fun assertBd(expected: String, actual: BigDecimal?) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")

    @Test
    fun `getAccountValuations - sums market value per account, same currency as base`() {
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(eur))
        whenever(holdingService.getHoldingsByAccount(1L)).thenReturn(listOf(
            accountHolding(accountId = 10L, listingId = 100L, currency = eur, qty = "10"),
            accountHolding(accountId = 10L, listingId = 101L, currency = eur, qty = "5"),
            accountHolding(accountId = 20L, listingId = 100L, currency = eur, qty = "2"),
        ))
        whenever(priceHistoryRepository.findLatestByListingId(100L)).thenReturn(PricePoint(LocalDate.now(), BigDecimal("50")))
        whenever(priceHistoryRepository.findLatestByListingId(101L)).thenReturn(PricePoint(LocalDate.now(), BigDecimal("20")))

        val valuations = service.getAccountValuations(1L)

        assertEquals(2, valuations.size)
        assertBd("600", valuations.first { it.accountId == 10L }.marketValueBase) // 10*50 + 5*20
        assertBd("100", valuations.first { it.accountId == 20L }.marketValueBase) // 2*50
    }

    @Test
    fun `getAccountValuations - FX-normalizes a listing priced in a non-base currency`() {
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(eur))
        whenever(holdingService.getHoldingsByAccount(1L)).thenReturn(listOf(
            accountHolding(accountId = 10L, listingId = 100L, currency = usd, qty = "10"),
        ))
        whenever(priceHistoryRepository.findLatestByListingId(100L)).thenReturn(PricePoint(LocalDate.now(), BigDecimal("100")))
        whenever(fxRateRepository.findLatest(eur, usd)).thenReturn(BigDecimal("2")) // 1 EUR = 2 USD

        val valuations = service.getAccountValuations(1L)

        assertBd("500", valuations[0].marketValueBase) // (10*100) / 2
    }

    @Test
    fun `getAccountValuations - a listing missing a price does not null out the rest of the account`() {
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(eur))
        whenever(holdingService.getHoldingsByAccount(1L)).thenReturn(listOf(
            accountHolding(accountId = 10L, listingId = 100L, currency = eur, qty = "10"),
            accountHolding(accountId = 10L, listingId = 101L, currency = eur, qty = "5"),
        ))
        whenever(priceHistoryRepository.findLatestByListingId(100L)).thenReturn(PricePoint(LocalDate.now(), BigDecimal("50")))
        whenever(priceHistoryRepository.findLatestByListingId(101L)).thenReturn(null)

        val valuations = service.getAccountValuations(1L)

        assertBd("500", valuations[0].marketValueBase)
    }

    @Test
    fun `getAccountValuations - an account with no priced holdings reports a null market value`() {
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(eur))
        whenever(holdingService.getHoldingsByAccount(1L)).thenReturn(listOf(
            accountHolding(accountId = 10L, listingId = 100L, currency = eur, qty = "10"),
        ))
        whenever(priceHistoryRepository.findLatestByListingId(100L)).thenReturn(null)

        val valuations = service.getAccountValuations(1L)

        assertEquals(1, valuations.size)
        assertEquals(null, valuations[0].marketValueBase)
    }
}
