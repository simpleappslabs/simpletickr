package com.simpletickr.transaction

import org.springframework.stereotype.Service

@Service
class AmendTransactionUseCase(private val transactionRepository: TransactionRepository) {

    fun execute(portfolioId: Long, id: Long, command: AmendTransactionCommand): Transaction? {
        val existing = transactionRepository.findById(id) ?: return null
        if (existing.portfolioId != portfolioId) return null
        val amended = existing.copy(
            assetId = command.assetId,
            type = command.type,
            quantity = command.quantity,
            price = command.price,
            date = command.date,
            fees = command.fees,
        )
        return transactionRepository.update(amended)
    }
}
