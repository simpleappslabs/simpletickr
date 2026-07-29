package com.simpletickr.portfolio.usecase

import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.persistence.PortfolioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdatePortfolioUseCase(private val portfolioRepository: PortfolioRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long, name: String, userId: Long): Portfolio? {
        if (!portfolioRepository.isOwnedBy(id, userId)) return null
        log.info("Updating portfolio id={}", id)
        return portfolioRepository.update(id, name)
    }
}
