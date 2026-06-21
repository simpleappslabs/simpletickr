package com.simpletickr.portfolio

import com.simpletickr.asset.AssetRepository
import com.simpletickr.gains.RealizedGainsCalculator
import com.simpletickr.gains.RealizationMethod
import com.simpletickr.gains.RealizedGainEntry
import com.simpletickr.gains.RealizedGainsReport
import com.simpletickr.generated.api.PortfoliosApi
import com.simpletickr.generated.model.PortfolioRequest
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.transaction.TransactionRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import com.simpletickr.generated.model.CurrencyTotal as GeneratedCurrencyTotal
import com.simpletickr.generated.model.Holding as HoldingModel
import com.simpletickr.generated.model.ListingHolding as ListingHoldingModel
import com.simpletickr.generated.model.Portfolio as PortfolioModel
import com.simpletickr.generated.model.RealizationMethod as GeneratedRealizationMethod
import com.simpletickr.generated.model.RealizedGainEntry as GeneratedRealizedGainEntry
import com.simpletickr.generated.model.RealizedGainsReport as GeneratedRealizedGainsReport

@RestController
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val valuationService: ValuationService,
    private val transactionRepository: TransactionRepository,
    private val assetRepository: AssetRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : PortfoliosApi {

    override fun listPortfolios(): ResponseEntity<List<PortfolioModel>> =
        ResponseEntity.ok(portfolioRepository.findAll().map { it.toModel() })

    override fun getPortfolio(id: Long): ResponseEntity<PortfolioModel> {
        val portfolio = portfolioRepository.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(portfolio.toModel())
    }

    override fun createPortfolio(portfolioRequest: PortfolioRequest): ResponseEntity<PortfolioModel> {
        val portfolio = portfolioRepository.save(portfolioRequest.name)
        return ResponseEntity.status(201).body(portfolio.toModel())
    }

    override fun updatePortfolio(id: Long, portfolioRequest: PortfolioRequest): ResponseEntity<PortfolioModel> {
        val portfolio = portfolioRepository.update(id, portfolioRequest.name)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(portfolio.toModel())
    }

    override fun deletePortfolio(id: Long): ResponseEntity<Unit> {
        if (portfolioRepository.findById(id) == null) return ResponseEntity.notFound().build()
        portfolioRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    override fun getHoldings(id: Long): ResponseEntity<List<HoldingModel>> {
        if (portfolioRepository.findById(id) == null) return ResponseEntity.notFound().build()
        val baseCurrency = userSettingsRepository.find().baseCurrency
        // One HoldingWithValuation per listing (e.g. AAPL on NASDAQ and AAPL on a local exchange are separate)
        val perListing = valuationService.getHoldingsWithValuation(id)

        // Roll up per-listing results into one asset-level row; listings are exposed as a drill-down array
        val assetHoldings = perListing
            .groupBy { it.holding.assetId }
            .map { (_, items) ->
                val first = items.first().holding
                val totalQty = items.sumOf { it.holding.quantity }

                // Convert each listing's local cost to base currency using the FX rate ValuationService resolved.
                // If any listing is missing an FX rate (fx_rates table empty), the whole asset cost becomes null.
                val allCostBase = items.map { hwv ->
                    val fx = hwv.fxUsed
                    if (hwv.holding.currency == baseCurrency) hwv.holding.totalCostLocal
                    else fx?.let { hwv.holding.totalCostLocal / it }
                }
                val totalCostBase = if (allCostBase.any { it == null }) null
                                    else allCostBase.filterNotNull().fold(BigDecimal.ZERO) { acc, v -> acc + v }
                val avgCostBasisBase = totalCostBase?.let {
                    if (totalQty > BigDecimal.ZERO) it.divide(totalQty, 10, RoundingMode.HALF_UP) else null
                }

                // Market value is null when no price history exists for the asset
                val allMarketValue = items.map { it.marketValueBase }
                val totalMarketValue = if (allMarketValue.any { it == null }) null
                                       else allMarketValue.filterNotNull().fold(BigDecimal.ZERO) { acc, v -> acc + v }
                val totalUnrealizedPnl = totalMarketValue?.let { mv ->
                    totalCostBase?.let { cb -> mv - cb }
                }
                val totalUnrealizedPct = totalUnrealizedPnl?.let { pnl ->
                    totalCostBase?.let { cb ->
                        if (cb > BigDecimal.ZERO) pnl.divide(cb, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100")) else null
                    }
                }

                HoldingModel(
                    assetId = first.assetId,
                    assetName = first.assetName,
                    baseCurrency = baseCurrency.value,
                    totalQuantity = totalQty.toDouble(),
                    avgCostBasisBase = avgCostBasisBase?.toDouble(),
                    totalCostBase = totalCostBase?.toDouble(),
                    marketValueBase = totalMarketValue?.toDouble(),
                    unrealizedPnlBase = totalUnrealizedPnl?.toDouble(),
                    unrealizedPnlPct = totalUnrealizedPct?.toDouble(),
                    listings = items.map { it.toListingModel() },
                )
            }

        return ResponseEntity.ok(assetHoldings)
    }

    override fun getRealizedGains(
        method: GeneratedRealizationMethod,
        from: LocalDate,
        to: LocalDate,
        id: Long,
    ): ResponseEntity<GeneratedRealizedGainsReport> {
        if (portfolioRepository.findById(id) == null) return ResponseEntity.notFound().build()
        val transactions = transactionRepository.findAll(id)
        val listingMap = assetRepository.findAll().flatMap { it.listings }.associateBy { it.id }
        val domainMethod = RealizationMethod.valueOf(method.value)
        val report = RealizedGainsCalculator.compute(transactions, listingMap, domainMethod, from, to)
        return ResponseEntity.ok(report.toModel())
    }

    private fun Portfolio.toModel() = PortfolioModel(id = id, name = name)

    private fun HoldingWithValuation.toListingModel() = ListingHoldingModel(
        listingId = holding.listingId,
        exchange = holding.exchange,
        ticker = holding.ticker,
        currency = holding.currency.value,
        quantity = holding.quantity.toDouble(),
        avgCostLocal = holding.avgCostLocal.toDouble(),
        totalCostLocal = holding.totalCostLocal.toDouble(),
        marketValueLocal = marketValueLocal?.toDouble(),
        fxRate = fxUsed?.toDouble(),
        marketValueBase = marketValueBase?.toDouble(),
    )

    private fun RealizedGainsReport.toModel() = GeneratedRealizedGainsReport(
        method = GeneratedRealizationMethod.valueOf(method.name),
        from = from,
        to = to,
        propertyEntries = entries.map { it.toModel() },
        byCurrency = byCurrency.entries.associate { (k, ct) ->
            k.value to GeneratedCurrencyTotal(
                currency = ct.currency.value,
                tradeCount = ct.tradeCount,
                totalProceeds = ct.totalProceeds.toDouble(),
                totalCostBasis = ct.totalCostBasis.toDouble(),
                totalGain = ct.totalGain.toDouble(),
            )
        },
    )

    private fun RealizedGainEntry.toModel() = GeneratedRealizedGainEntry(
        assetId = assetId,
        ticker = ticker,
        currency = currency.value,
        date = date,
        quantity = quantity.toDouble(),
        proceeds = proceeds.toDouble(),
        buyFees = buyFees.toDouble(),
        sellFees = sellFees.toDouble(),
        costBasis = costBasis.toDouble(),
        gain = gain.toDouble(),
    )
}
