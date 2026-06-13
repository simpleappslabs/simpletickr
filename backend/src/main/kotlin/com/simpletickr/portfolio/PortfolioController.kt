package com.simpletickr.portfolio

import com.simpletickr.asset.AssetRepository
import com.simpletickr.generated.api.PortfoliosApi
import com.simpletickr.generated.model.PortfolioRequest
import com.simpletickr.transaction.TransactionRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import com.simpletickr.generated.model.Holding as HoldingModel
import com.simpletickr.generated.model.Portfolio as PortfolioModel
import com.simpletickr.generated.model.RealizationMethod as GeneratedRealizationMethod
import com.simpletickr.generated.model.RealizedGainEntry as GeneratedRealizedGainEntry
import com.simpletickr.generated.model.RealizedGainsReport as GeneratedRealizedGainsReport

@RestController
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val holdingRepository: HoldingRepository,
    private val transactionRepository: TransactionRepository,
    private val assetRepository: AssetRepository,
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
        return ResponseEntity.ok(holdingRepository.findByPortfolioId(id).map { it.toModel() })
    }

    override fun getRealizedGains(
        method: GeneratedRealizationMethod,
        from: LocalDate,
        to: LocalDate,
        id: Long,
    ): ResponseEntity<GeneratedRealizedGainsReport> {
        if (portfolioRepository.findById(id) == null) return ResponseEntity.notFound().build()
        val transactions = transactionRepository.findAll(id)
        val assetMap = assetRepository.findAll().associateBy { it.id }
        val domainMethod = RealizationMethod.valueOf(method.value)
        val report = RealizedGainsCalculator.compute(transactions, assetMap, domainMethod, from, to)
        return ResponseEntity.ok(report.toModel())
    }

    private fun Portfolio.toModel() = PortfolioModel(id = id, name = name)

    private fun Holding.toModel() = HoldingModel(
        assetId = assetId,
        ticker = ticker,
        name = name,
        quantity = quantity.toDouble(),
        avgCostBasis = avgCostBasis.toDouble(),
        totalCost = totalCost.toDouble(),
        unrealizedGain = unrealizedGain?.toDouble(),
    )

    private fun RealizedGainsReport.toModel() = GeneratedRealizedGainsReport(
        method = GeneratedRealizationMethod.valueOf(method.name),
        from = from,
        to = to,
        propertyEntries = entries.map { it.toModel() },
        totalProceeds = totalProceeds.toDouble(),
        totalBuyFees = totalBuyFees.toDouble(),
        totalSellFees = totalSellFees.toDouble(),
        totalCostBasis = totalCostBasis.toDouble(),
        totalGain = totalGain.toDouble(),
    )

    private fun RealizedGainEntry.toModel() = GeneratedRealizedGainEntry(
        assetId = assetId,
        ticker = ticker,
        date = date,
        quantity = quantity.toDouble(),
        proceeds = proceeds.toDouble(),
        buyFees = buyFees.toDouble(),
        sellFees = sellFees.toDouble(),
        costBasis = costBasis.toDouble(),
        gain = gain.toDouble(),
    )
}
