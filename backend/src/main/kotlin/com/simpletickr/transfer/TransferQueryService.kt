package com.simpletickr.transfer

import com.simpletickr.portfolio.PortfolioQueryService
import org.springframework.stereotype.Service

@Service
class TransferQueryService(
    private val transferRepository: TransferRepository,
    private val portfolioQueryService: PortfolioQueryService,
) {

    fun listTransfersForPortfolio(portfolioId: Long, userId: Long): List<Transfer>? {
        if (!portfolioQueryService.isOwnedBy(portfolioId, userId)) return null
        return transferRepository.findAllForPortfolio(portfolioId)
    }
}
