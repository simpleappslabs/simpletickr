package com.simpletickr.portfolio

import org.springframework.stereotype.Service
import java.math.BigDecimal

// Narrow query for "what did we pay for this position", distinct from HoldingService's
// broader "what does the portfolio currently hold" read model. Kept separate so callers
// that only need acquisition cost (e.g. RecordTransferUseCase) don't depend on the general
// holdings projection — even though today it's implemented in terms of it.
@Service
class CostBasisService(private val holdingService: HoldingService) {

    fun currentAverageCost(portfolioId: Long, listingId: Long): BigDecimal? =
        holdingService.getHoldings(portfolioId).find { it.listingId == listingId }?.avgCostLocal
}
