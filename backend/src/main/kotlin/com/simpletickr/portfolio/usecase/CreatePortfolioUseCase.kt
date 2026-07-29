package com.simpletickr.portfolio.usecase

import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.persistence.PortfolioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CreatePortfolioUseCase(private val portfolioRepository: PortfolioRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(name: String, userId: Long): Portfolio {
        log.info("Creating portfolio name={}, userId={}", name, userId)
        return portfolioRepository.save(name, userId)
    }
}
