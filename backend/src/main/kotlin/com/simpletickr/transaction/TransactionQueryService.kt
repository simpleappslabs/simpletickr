package com.simpletickr.transaction

import com.simpletickr.portfolio.PortfolioQueryService
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.persistence.TransactionFilter
import com.simpletickr.transaction.persistence.TransactionRepository
import org.springframework.stereotype.Service

data class TransactionPageResult(val items: List<Transaction>, val total: Long)

@Service
class TransactionQueryService(
    private val transactionRepository: TransactionRepository,
    private val portfolioQueryService: PortfolioQueryService,
) {

    fun listTransactions(filter: TransactionFilter, page: Int, size: Int, userId: Long): TransactionPageResult? {
        if (filter.portfolioId != null && !portfolioQueryService.isOwnedBy(filter.portfolioId, userId)) return null

        val resolvedFilter = if (filter.portfolioId == null) {
            filter.copy(portfolioIds = portfolioQueryService.listPortfolios(userId).map { it.id }.toSet())
        } else filter

        val items = transactionRepository.findAll(resolvedFilter, page, size)
        val total = transactionRepository.count(resolvedFilter)
        return TransactionPageResult(items, total)
    }

    fun getTransaction(id: Long, userId: Long): Transaction? =
        transactionRepository.findById(id)?.takeIf { portfolioQueryService.isOwnedBy(it.portfolioId, userId) }
}
