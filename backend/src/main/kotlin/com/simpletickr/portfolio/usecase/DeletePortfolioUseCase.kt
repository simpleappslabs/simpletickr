package com.simpletickr.portfolio.usecase

import com.simpletickr.portfolio.persistence.PortfolioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeletePortfolioUseCase(private val portfolioRepository: PortfolioRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long, userId: Long): Boolean {
        if (!portfolioRepository.isOwnedBy(id, userId)) return false
        log.info("Deleting portfolio id={}", id)
        portfolioRepository.delete(id)
        return true
    }
}
