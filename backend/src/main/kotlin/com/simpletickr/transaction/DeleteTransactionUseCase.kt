package com.simpletickr.transaction

import org.springframework.stereotype.Service

@Service
class DeleteTransactionUseCase(private val transactionRepository: TransactionRepository) {

    fun execute(portfolioId: Long, id: Long): Boolean {
        val existing = transactionRepository.findById(id) ?: return false
        if (existing.portfolioId != portfolioId) return false
        transactionRepository.delete(id)
        return true
    }
}
