package com.simpletickr.transfer

import com.simpletickr.asset.model.Asset
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.fx.FxRateService
import com.simpletickr.portfolio.CostBasisService
import com.simpletickr.portfolio.HoldingService
import com.simpletickr.portfolio.model.Holding
import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.RecordTransferCommand
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

class RecordTransferUseCaseTest {

    private val transferRepository = mock<TransferRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val assetRepository = mock<AssetRepository>()
    private val portfolioRepository = mock<PortfolioRepository>()
    private val holdingService = mock<HoldingService>()
    private val costBasisService = mock<CostBasisService>()
    private val fxRateService = mock<FxRateService>()
    private val userSettingsRepository = mock<UserSettingsRepository>()

    private val useCase = RecordTransferUseCase(
        transferRepository, transactionRepository, listingRepository, assetRepository,
        portfolioRepository, holdingService, costBasisService, fxRateService, userSettingsRepository,
    )

    private val date = LocalDate.of(2024, 6, 1)
    private val ethListing = Listing(id = 20L, assetId = 2L, exchange = null, ticker = "ETH", currency = CurrencyCode("USD"))
    private val ethAsset = Asset(id = 2L, uuid = UUID.randomUUID(), isin = null, name = "Ethereum", type = AssetType.CRYPTO)
    private val destinationPortfolio = Portfolio(id = 7L, uuid = UUID.randomUUID(), name = "Cold Storage")

    private val existingHolding = Holding(
        assetId = 2L, assetName = "Ethereum", listingId = 20L, exchange = null, ticker = "ETH",
        currency = CurrencyCode("USD"), quantity = BigDecimal("2.0"),
        avgCostLocal = BigDecimal("2000"), totalCostLocal = BigDecimal("4000"),
    )

    private val command = RecordTransferCommand(
        listingId = 20L,
        quantity = BigDecimal("1.0"),
        assetFeeQuantity = BigDecimal("0.005"),
        date = date,
        sourceAccountId = 1L,
        destinationAccountId = 2L,
        destinationPortfolioId = 7L,
    )

    init {
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(CurrencyCode("USD")))
        whenever(transferRepository.create()).thenReturn(900L)
        whenever(listingRepository.findById(20L)).thenReturn(ethListing)
        whenever(assetRepository.findById(2L)).thenReturn(ethAsset)
        whenever(portfolioRepository.findById(7L)).thenReturn(destinationPortfolio)
        whenever(holdingService.getHoldings(5L)).thenReturn(listOf(existingHolding))
        whenever(costBasisService.currentAverageCost(5L, 20L)).thenReturn(BigDecimal("2000"))
        whenever(transactionRepository.save(any())).thenAnswer { inv ->
            val tx = inv.getArgument<Transaction>(0)
            tx.copy(id = if (tx.type == TransactionType.TRANSFER_OUT) 101L else 102L)
        }
    }

    @Test
    fun `happy path splits quantity by the asset fee and freezes cost basis on both legs`() {
        val result = useCase.execute(5L, command)

        assertEquals(900L, result.id)
        assertEquals(TransactionType.TRANSFER_OUT, result.sourceLeg.type)
        assertEquals(TransactionType.TRANSFER_IN, result.destinationLeg.type)
        assertEquals(5L, result.sourceLeg.portfolioId)
        assertEquals(7L, result.destinationLeg.portfolioId)
        assertEquals(1L, result.sourceLeg.accountId)
        assertEquals(2L, result.destinationLeg.accountId)

        assertBd("1.0", result.sourceLeg.quantity)
        assertBd("2000", result.sourceLeg.price)
        assertBd("0.005", result.sourceLeg.assetFeeQuantity!!)

        // received = 1.0 - 0.005 = 0.995; total basis stays 1.0 * 2000 = 2000, spread over 0.995
        assertBd("0.995", result.destinationLeg.quantity)
        val expectedPrice = BigDecimal("1.0").multiply(BigDecimal("2000"))
            .divide(BigDecimal("0.995"), 10, java.math.RoundingMode.HALF_UP)
        assertEquals(0, expectedPrice.compareTo(result.destinationLeg.price))
        // same total basis on both legs: sourceQty * sourcePrice == destQty * destPrice
        val sourceBasis = result.sourceLeg.quantity * result.sourceLeg.price
        val destBasis = result.destinationLeg.quantity * result.destinationLeg.price
        assertEquals(0, sourceBasis.setScale(2, java.math.RoundingMode.HALF_UP)
            .compareTo(destBasis.setScale(2, java.math.RoundingMode.HALF_UP)))
    }

    @Test
    fun `throws when source and destination accounts are the same`() {
        val sameAccount = command.copy(destinationAccountId = 1L)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, sameAccount) }
    }

    @Test
    fun `throws when listing not found`() {
        whenever(listingRepository.findById(20L)).thenReturn(null)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command) }
    }

    @Test
    fun `throws when destination portfolio not found`() {
        whenever(portfolioRepository.findById(7L)).thenReturn(null)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command) }
    }

    @Test
    fun `throws when there is no holding of the listing in the source portfolio`() {
        whenever(holdingService.getHoldings(5L)).thenReturn(emptyList())
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command) }
    }

    @Test
    fun `throws when transferring more than is held`() {
        val tooMuch = command.copy(quantity = BigDecimal("10"))
        assertThrows<IllegalArgumentException> { useCase.execute(5L, tooMuch) }
    }

    @Test
    fun `throws when asset fee leaves a zero or negative received quantity`() {
        val allFee = command.copy(quantity = BigDecimal("1.0"), assetFeeQuantity = BigDecimal("1.0"))
        assertThrows<IllegalArgumentException> { useCase.execute(5L, allFee) }
    }

    @Test
    fun `no fee - destination receives the full quantity at the same per-unit price`() {
        val noFee = command.copy(assetFeeQuantity = null)
        val result = useCase.execute(5L, noFee)

        assertBd("1.0", result.destinationLeg.quantity)
        assertBd("2000", result.destinationLeg.price)
    }

    @Test
    fun `resolves FX rate for foreign-currency listing on both legs`() {
        val eurListing = ethListing.copy(currency = CurrencyCode("EUR"))
        whenever(listingRepository.findById(20L)).thenReturn(eurListing)
        whenever(fxRateService.lookupOrFetch(CurrencyCode("USD"), CurrencyCode("EUR"), date))
            .thenReturn(com.simpletickr.fx.model.FxRate(CurrencyCode("USD"), CurrencyCode("EUR"), date, BigDecimal("0.92")))

        val result = useCase.execute(5L, command)

        assertBd("0.92", result.sourceLeg.fxRate!!)
        assertBd("0.92", result.destinationLeg.fxRate!!)
    }

    private fun assertBd(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "Expected $expected but got $actual")
}
