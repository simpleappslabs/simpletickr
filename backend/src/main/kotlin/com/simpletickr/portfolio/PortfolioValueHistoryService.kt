package com.simpletickr.portfolio

import com.simpletickr.portfolio.model.PortfolioValuePoint
import com.simpletickr.portfolio.persistence.PortfolioValueHistoryRepository
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PortfolioValueHistoryService(
    private val repository: PortfolioValueHistoryRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    fun getValueHistory(
        portfolioId: Long,
        from: LocalDate?,
        to: LocalDate,
    ): Pair<CurrencyCode, List<PortfolioValuePoint>> {
        val baseCurrency = userSettingsRepository.find().baseCurrency
        val effectiveFrom = from ?: repository.findOldestTransactionDate(portfolioId)
            ?: return baseCurrency to emptyList()
        return baseCurrency to repository.findValueHistory(portfolioId, baseCurrency, effectiveFrom, to)
    }
}
