package com.simpletickr.portfolio

import com.simpletickr.account.AccountService
import com.simpletickr.account.model.Account
import com.simpletickr.auth.currentUser
import com.simpletickr.gains.RealizationMethod
import com.simpletickr.gains.RealizedGainEntry
import com.simpletickr.gains.RealizedGainLot
import com.simpletickr.gains.RealizedGainsReport
import com.simpletickr.generated.api.PortfoliosApi
import com.simpletickr.generated.model.PortfolioRequest
import com.simpletickr.portfolio.model.AccountValuation
import com.simpletickr.portfolio.model.AssetHolding
import com.simpletickr.portfolio.model.HoldingWithValuation
import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.model.PortfolioValuationSummary
import com.simpletickr.portfolio.usecase.CreatePortfolioUseCase
import com.simpletickr.portfolio.usecase.DeletePortfolioUseCase
import com.simpletickr.portfolio.usecase.UpdatePortfolioUseCase
import com.simpletickr.price.usecase.BackfillPortfolioPricesUseCase
import com.simpletickr.settings.SettingsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.SyncResult as SyncResultModel
import java.time.LocalDate
import com.simpletickr.generated.model.AccountAllocation as AccountAllocationModel
import com.simpletickr.generated.model.AccountType as GeneratedAccountType
import com.simpletickr.generated.model.CurrencyTotal as GeneratedCurrencyTotal
import com.simpletickr.generated.model.Holding as HoldingModel
import com.simpletickr.generated.model.ListingHolding as ListingHoldingModel
import com.simpletickr.generated.model.Portfolio as PortfolioModel
import com.simpletickr.generated.model.RealizationMethod as GeneratedRealizationMethod
import com.simpletickr.generated.model.RealizedGainEntry as GeneratedRealizedGainEntry
import com.simpletickr.generated.model.RealizedGainLot as GeneratedRealizedGainLot
import com.simpletickr.generated.model.RealizedGainsReport as GeneratedRealizedGainsReport
import com.simpletickr.generated.model.PortfolioValueHistory as PortfolioValueHistoryModel
import com.simpletickr.generated.model.PortfolioValuePoint as PortfolioValuePointModel
import com.simpletickr.generated.model.PortfolioValuationSummary as PortfolioValuationSummaryModel

@RestController
class PortfolioController(
    private val portfolioQueryService: PortfolioQueryService,
    private val createPortfolioUseCase: CreatePortfolioUseCase,
    private val updatePortfolioUseCase: UpdatePortfolioUseCase,
    private val deletePortfolioUseCase: DeletePortfolioUseCase,
    private val valuationService: ValuationService,
    private val realizedGainsService: RealizedGainsService,
    private val accountService: AccountService,
    private val settingsService: SettingsService,
    private val backfillPortfolioPricesUseCase: BackfillPortfolioPricesUseCase,
    private val portfolioValueHistoryService: PortfolioValueHistoryService,
) : PortfoliosApi {

    override fun listPortfolios(): ResponseEntity<List<PortfolioModel>> =
        ResponseEntity.ok(portfolioQueryService.listPortfolios(currentUser().id).map { it.toModel() })

    override fun getPortfolio(id: Long): ResponseEntity<PortfolioModel> {
        val portfolio = portfolioQueryService.getPortfolio(id, currentUser().id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(portfolio.toModel())
    }

    override fun createPortfolio(portfolioRequest: PortfolioRequest): ResponseEntity<PortfolioModel> {
        val portfolio = createPortfolioUseCase.execute(portfolioRequest.name, currentUser().id)
        return ResponseEntity.status(201).body(portfolio.toModel())
    }

    override fun updatePortfolio(id: Long, portfolioRequest: PortfolioRequest): ResponseEntity<PortfolioModel> {
        val portfolio = updatePortfolioUseCase.execute(id, portfolioRequest.name, currentUser().id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(portfolio.toModel())
    }

    override fun deletePortfolio(id: Long): ResponseEntity<Unit> {
        if (!deletePortfolioUseCase.execute(id, currentUser().id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    override fun syncPortfolioPrices(id: Long): ResponseEntity<SyncResultModel> {
        if (!portfolioQueryService.isOwnedBy(id, currentUser().id)) return ResponseEntity.notFound().build()
        val result = backfillPortfolioPricesUseCase.execute(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(SyncResultModel(synced = result.synced, failed = result.failed))
    }

    override fun getHoldings(id: Long): ResponseEntity<List<HoldingModel>> {
        if (!portfolioQueryService.isOwnedBy(id, currentUser().id)) return ResponseEntity.notFound().build()
        val baseCurrency = settingsService.getSettings(currentUser().id).baseCurrency
        val assetHoldings = valuationService.getAssetHoldings(id, currentUser().id)
        return ResponseEntity.ok(assetHoldings.map { it.toModel(baseCurrency.value) })
    }

    override fun getAccountAllocation(id: Long): ResponseEntity<List<AccountAllocationModel>> {
        if (!portfolioQueryService.isOwnedBy(id, currentUser().id)) return ResponseEntity.notFound().build()
        val baseCurrency = settingsService.getSettings(currentUser().id).baseCurrency
        val accountsById = accountService.listAccounts(currentUser().id).associateBy { it.id }
        val allocations = valuationService.getAccountValuations(id, currentUser().id)
        return ResponseEntity.ok(allocations.mapNotNull { it.toModel(baseCurrency.value, accountsById) })
    }

    override fun getPortfolioValuationSummary(id: Long): ResponseEntity<PortfolioValuationSummaryModel> {
        if (!portfolioQueryService.isOwnedBy(id, currentUser().id)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(valuationService.getValuationSummary(id, currentUser().id).toModel())
    }

    override fun getPortfolioValueHistory(to: LocalDate, id: Long, from: LocalDate?): ResponseEntity<PortfolioValueHistoryModel> {
        if (!portfolioQueryService.isOwnedBy(id, currentUser().id)) return ResponseEntity.notFound().build()
        val (baseCurrency, points) = portfolioValueHistoryService.getValueHistory(id, from, to, currentUser().id)
        return ResponseEntity.ok(
            PortfolioValueHistoryModel(
                baseCurrency = baseCurrency.value,
                points = points.map { pt ->
                    PortfolioValuePointModel(date = pt.date, `value` = pt.value?.toDouble(), invested = pt.invested?.toDouble())
                },
            )
        )
    }

    override fun getRealizedGains(
        method: GeneratedRealizationMethod,
        from: LocalDate,
        to: LocalDate,
        id: Long,
    ): ResponseEntity<GeneratedRealizedGainsReport> {
        if (!portfolioQueryService.isOwnedBy(id, currentUser().id)) return ResponseEntity.notFound().build()
        val domainMethod = RealizationMethod.valueOf(method.value)
        val report = realizedGainsService.getRealizedGains(id, domainMethod, from, to)
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

    private fun AssetHolding.toModel(baseCurrency: String) = HoldingModel(
        assetId = assetId,
        assetName = assetName,
        baseCurrency = baseCurrency,
        totalQuantity = totalQuantity.toDouble(),
        avgCostBasisBase = avgCostBasisBase?.toDouble(),
        totalCostBase = totalCostBase?.toDouble(),
        marketValueBase = marketValueBase?.toDouble(),
        unrealizedPnlBase = unrealizedPnlBase?.toDouble(),
        unrealizedPnlPct = unrealizedPnlPct?.toDouble(),
        listings = listings.map { it.toListingModel() },
    )

    private fun AccountValuation.toModel(baseCurrency: String, accountsById: Map<Long, Account>): AccountAllocationModel? {
        val account = accountsById[accountId] ?: return null
        return AccountAllocationModel(
            accountId = accountId,
            accountName = account.name,
            accountType = GeneratedAccountType.valueOf(account.accountType.name),
            baseCurrency = baseCurrency,
            marketValueBase = marketValueBase?.toDouble(),
        )
    }

    private fun PortfolioValuationSummary.toModel() = PortfolioValuationSummaryModel(
        totalCostBase = totalCostBase.toDouble(),
        totalMarketValueBase = totalMarketValueBase?.toDouble(),
        totalUnrealizedPnlBase = totalUnrealizedPnlBase?.toDouble(),
        totalUnrealizedPnlPct = totalUnrealizedPnlPct?.toDouble(),
        excludedHoldingCount = excludedHoldingCount,
        excludedHoldingNames = excludedHoldingNames,
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
        tradeId = tradeId,
        receivedTicker = receivedTicker,
        lots = lots.map { it.toModel() },
    )

    private fun RealizedGainLot.toModel() = GeneratedRealizedGainLot(
        acquisitionDate = acquisitionDate,
        quantity = quantity.toDouble(),
        pricePerUnit = pricePerUnit.toDouble(),
        buyFees = buyFees.toDouble(),
        costBasis = costBasis.toDouble(),
    )
}
