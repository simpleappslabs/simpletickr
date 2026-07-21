package com.simpletickr.portfolio

import com.simpletickr.fx.persistence.FxRateRepository
import com.simpletickr.portfolio.model.AssetHolding
import com.simpletickr.portfolio.model.Holding
import com.simpletickr.portfolio.model.HoldingWithValuation
import com.simpletickr.portfolio.model.PortfolioValuationSummary
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ValuationService(
    private val holdingService: HoldingService,
    private val priceHistoryRepository: AssetPriceHistoryRepository,
    private val fxRateRepository: FxRateRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {

    fun getHoldingsWithValuation(portfolioId: Long): List<HoldingWithValuation> {
        val baseCurrency = userSettingsRepository.find().baseCurrency
        return holdingService.getHoldings(portfolioId).map { holding ->
            valuate(holding, baseCurrency)
        }
    }

    // One AssetHolding per asset — multiple listings of the same asset (e.g. cross-listed on two
    // exchanges) are rolled up together, with a partial sum rather than an all-or-nothing null
    // when some listings lack price/FX data (see PortfolioValuationCalculator).
    fun getAssetHoldings(portfolioId: Long): List<AssetHolding> {
        val baseCurrency = userSettingsRepository.find().baseCurrency
        return PortfolioValuationCalculator.rollUpByAsset(getHoldingsWithValuation(portfolioId), baseCurrency)
    }

    fun getValuationSummary(portfolioId: Long): PortfolioValuationSummary =
        PortfolioValuationCalculator.summarize(getAssetHoldings(portfolioId))

    private fun valuate(holding: Holding, baseCurrency: CurrencyCode): HoldingWithValuation {
        val latestPrice = priceHistoryRepository.findLatestByListingId(holding.listingId)?.price
        val fxRate = if (holding.currency == baseCurrency) null
                     else fxRateRepository.findLatest(baseCurrency, holding.currency)

        val marketValueLocal = latestPrice?.let { it * holding.quantity }
        val marketValueBase = marketValueLocal?.let { toBase(it, fxRate) }

        val costBase = toBase(holding.totalCostLocal, fxRate)
        val unrealizedPnlBase = marketValueBase?.let { it - costBase }
        val unrealizedPnlPct = unrealizedPnlBase?.let {
            if (costBase > BigDecimal.ZERO)
                it.divide(costBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
            else null
        }

        return HoldingWithValuation(
            holding = holding,
            marketValueLocal = marketValueLocal,
            marketValueBase = marketValueBase,
            unrealizedPnlBase = unrealizedPnlBase,
            unrealizedPnlPct = unrealizedPnlPct,
            fxUsed = fxRate,
        )
    }

    // FX helper: if fx is null, currencies match — no conversion needed.
    private fun toBase(amount: BigDecimal, fx: BigDecimal?): BigDecimal =
        fx?.let { amount / it } ?: amount
}
