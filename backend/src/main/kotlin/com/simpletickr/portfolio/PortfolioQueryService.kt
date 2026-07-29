package com.simpletickr.portfolio

import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.persistence.PortfolioRepository
import org.springframework.stereotype.Service

@Service
class PortfolioQueryService(private val portfolioRepository: PortfolioRepository) {

    fun listPortfolios(userId: Long): List<Portfolio> = portfolioRepository.findAllForUser(userId)

    fun getPortfolio(id: Long, userId: Long): Portfolio? =
        portfolioRepository.findById(id)?.takeIf { it.userId == userId }

    fun isOwnedBy(id: Long, userId: Long): Boolean = portfolioRepository.isOwnedBy(id, userId)
}
