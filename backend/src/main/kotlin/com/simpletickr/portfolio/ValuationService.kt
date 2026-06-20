package com.simpletickr.portfolio

import com.simpletickr.fx.FxRateRepository
import com.simpletickr.price.AssetPriceHistoryRepository
import com.simpletickr.settings.UserSettingsRepository
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

    private fun valuate(holding: Holding, baseCurrency: String): HoldingWithValuation {
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
