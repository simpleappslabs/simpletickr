package com.simpletickr.transaction.usecase

import com.simpletickr.trade.CryptoTradeRepository
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transfer.TransferRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val cryptoTradeRepository: CryptoTradeRepository,
    private val transferRepository: TransferRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(portfolioId: Long, id: Long): Boolean {
        log.info("Deleting transaction id={} from portfolio {}", id, portfolioId)
        val existing = transactionRepository.findById(id) ?: return false
        if (existing.portfolioId != portfolioId) return false
        if (existing.tradeId != null) {
            log.info("Transaction {} is part of trade {}, deleting entire trade", id, existing.tradeId)
            cryptoTradeRepository.delete(existing.tradeId)
        } else if (existing.transferId != null) {
            log.info("Transaction {} is part of transfer {}, deleting both legs", id, existing.transferId)
            transferRepository.delete(existing.transferId)
        } else {
            transactionRepository.delete(id)
        }
        return true
    }
}
