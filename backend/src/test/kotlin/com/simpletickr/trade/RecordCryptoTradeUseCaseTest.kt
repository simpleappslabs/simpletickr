package com.simpletickr.trade

import com.simpletickr.asset.model.Asset
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.fx.FxRateService
import com.simpletickr.fx.model.FxRate
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.RecordCryptoTradeCommand
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

class RecordCryptoTradeUseCaseTest {

    private val cryptoTradeRepository = mock<CryptoTradeRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val assetRepository = mock<AssetRepository>()
    private val fxRateService = mock<FxRateService>()
    private val userSettingsRepository = mock<UserSettingsRepository>()

    private val useCase = RecordCryptoTradeUseCase(
        cryptoTradeRepository, transactionRepository, listingRepository,
        assetRepository, fxRateService, userSettingsRepository,
    )

    private val date = LocalDate.of(2024, 6, 1)
    private val btcListing = Listing(id = 10L, assetId = 1L, exchange = null, ticker = "BTC", currency = CurrencyCode("USD"))
    private val ethListing = Listing(id = 20L, assetId = 2L, exchange = null, ticker = "ETH", currency = CurrencyCode("USD"))
    private val btcAsset = Asset(id = 1L, uuid = UUID.randomUUID(), isin = null, name = "Bitcoin", type = AssetType.CRYPTO)
    private val ethAsset = Asset(id = 2L, uuid = UUID.randomUUID(), isin = null, name = "Ethereum", type = AssetType.CRYPTO)

    private fun savedTx(id: Long, listingId: Long, assetId: Long, type: TransactionType, tradeId: Long) =
        Transaction(id, 5L, listingId, assetId, type, BigDecimal("1"), BigDecimal("50000"), date, null, tradeId = tradeId, accountId = 1L)

    init {
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(CurrencyCode("USD")))
        whenever(cryptoTradeRepository.create(any())).thenReturn(99L)
        whenever(listingRepository.findById(10L)).thenReturn(btcListing)
        whenever(listingRepository.findById(20L)).thenReturn(ethListing)
        whenever(assetRepository.findById(1L)).thenReturn(btcAsset)
        whenever(assetRepository.findById(2L)).thenReturn(ethAsset)
        whenever(transactionRepository.save(any())).thenAnswer { inv ->
            val tx = inv.getArgument<Transaction>(0)
            tx.copy(id = if (tx.type == TransactionType.SELL) 101L else 102L)
        }
    }

    private val command = RecordCryptoTradeCommand(
        sellListingId = 10L, sellQuantity = BigDecimal("0.1"), sellPrice = BigDecimal("60000"),
        buyListingId = 20L, buyQuantity = BigDecimal("2.5"), buyPrice = BigDecimal("2400"),
        date = date,
        accountId = 1L,
    )

    @Test
    fun `happy path returns trade with sell and buy legs`() {
        val result = useCase.execute(5L, command)

        assertEquals(99L, result.id)
        assertEquals(5L, result.portfolioId)
        assertEquals(TransactionType.SELL, result.sell.type)
        assertEquals(TransactionType.BUY, result.buy.type)
        assertEquals(99L, result.sell.tradeId)
        assertEquals(99L, result.buy.tradeId)
    }

    @Test
    fun `throws when sell listing not found`() {
        whenever(listingRepository.findById(10L)).thenReturn(null)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command) }
    }

    @Test
    fun `throws when buy listing not found`() {
        whenever(listingRepository.findById(20L)).thenReturn(null)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command) }
    }

    @Test
    fun `throws when sell listing is same as buy listing`() {
        val sameListingCommand = command.copy(buyListingId = 10L)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, sameListingCommand) }
    }

    @Test
    fun `throws when sell asset is not CRYPTO`() {
        whenever(assetRepository.findById(1L)).thenReturn(btcAsset.copy(type = AssetType.STOCK))
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command) }
    }

    @Test
    fun `throws when buy asset is not CRYPTO`() {
        whenever(assetRepository.findById(2L)).thenReturn(ethAsset.copy(type = AssetType.ETF))
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command) }
    }

    @Test
    fun `resolves FX rate for foreign currency legs`() {
        val eurSettings = UserSettings(CurrencyCode("EUR"))
        whenever(userSettingsRepository.find()).thenReturn(eurSettings)
        whenever(fxRateService.lookupOrFetch(CurrencyCode("EUR"), CurrencyCode("USD"), date))
            .thenReturn(FxRate(CurrencyCode("EUR"), CurrencyCode("USD"), date, BigDecimal("1.08")))

        val result = useCase.execute(5L, command)

        assertEquals(BigDecimal("1.08"), result.sell.fxRate)
        assertEquals(BigDecimal("1.08"), result.buy.fxRate)
    }
}
