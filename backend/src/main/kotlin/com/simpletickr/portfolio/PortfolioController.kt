package com.simpletickr.portfolio

import com.simpletickr.generated.api.PortfoliosApi
import com.simpletickr.generated.model.PortfolioRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.Holding as HoldingModel
import com.simpletickr.generated.model.Portfolio as PortfolioModel

@RestController
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val holdingRepository: HoldingRepository,
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
}