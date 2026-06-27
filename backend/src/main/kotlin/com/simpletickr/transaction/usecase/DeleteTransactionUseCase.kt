package com.simpletickr.transaction.usecase

import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.persistence.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteTransactionUseCase(private val transactionRepository: TransactionRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(portfolioId: Long, id: Long): Boolean {
        log.info("Deleting transaction id={} from portfolio {}", id, portfolioId)
        val existing = transactionRepository.findById(id) ?: return false
        if (existing.portfolioId != portfolioId) return false
        transactionRepository.delete(id)
        return true
    }
}
