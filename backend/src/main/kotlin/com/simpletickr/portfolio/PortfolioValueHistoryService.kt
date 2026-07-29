package com.simpletickr.portfolio

import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.fx.persistence.FxRateRepository
import com.simpletickr.portfolio.model.PortfolioValuePoint
import com.simpletickr.portfolio.persistence.PortfolioValueHistoryRepository
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PortfolioValueHistoryService(
    private val repository: PortfolioValueHistoryRepository,
    private val transactionRepository: TransactionRepository,
    private val assetRepository: AssetRepository,
    private val priceHistoryRepository: AssetPriceHistoryRepository,
    private val fxRateRepository: FxRateRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    fun getValueHistory(
        portfolioId: Long,
        from: LocalDate?,
        to: LocalDate,
        userId: Long,
    ): Pair<CurrencyCode, List<PortfolioValuePoint>> {
        val baseCurrency = userSettingsRepository.find(userId).baseCurrency
        val effectiveFrom = from ?: repository.findOldestTransactionDate(portfolioId)
            ?: return baseCurrency to emptyList()

        val transactions = transactionRepository.findAllForPortfolio(portfolioId)
        val listingMap = assetRepository.findAll().flatMap { it.listings }.associateBy { it.id }

        // One boundary row from before `effectiveFrom` (to seed day one of the fold) plus
        // everything actually inside the window — not the full history back to inception, which
        // would make a short window (e.g. "1M") on a long-lived listing refetch years of unused data.
        val heldListingIds = transactions.filter { it.type != TransactionType.SPLIT }.map { it.listingId }.distinct()
        val priceHistory = heldListingIds.associateWith { listingId ->
            listOfNotNull(priceHistoryRepository.findLatestBefore(listingId, effectiveFrom)) +
                priceHistoryRepository.findByListingId(listingId, effectiveFrom, to)
        }

        val heldCurrencies = heldListingIds.mapNotNull { listingMap[it]?.currency }.filter { it != baseCurrency }.distinct()
        val fxRateHistory = heldCurrencies.associateWith { currency ->
            listOfNotNull(fxRateRepository.findLatestBefore(baseCurrency, currency, effectiveFrom)) +
                fxRateRepository.findBetween(baseCurrency, currency, effectiveFrom, to)
        }

        val points = PortfolioValueHistoryCalculator.compute(
            transactions, listingMap, priceHistory, fxRateHistory, baseCurrency, effectiveFrom, to,
        )
        return baseCurrency to points
    }
}
