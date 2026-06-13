package com.simpletickr.transaction

import org.springframework.stereotype.Service

@Service
class RecordTransactionUseCase(private val transactionRepository: TransactionRepository) {

    fun execute(portfolioId: Long, command: RecordTransactionCommand): Transaction {
        val transaction = Transaction(
            id = 0L,
            portfolioId = portfolioId,
            assetId = command.assetId,
            type = command.type,
            quantity = command.quantity,
            price = command.price,
            date = command.date,
            fees = command.fees,
        )
        return transactionRepository.save(transaction)
    }
}
