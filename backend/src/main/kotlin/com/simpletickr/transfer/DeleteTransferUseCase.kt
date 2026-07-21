package com.simpletickr.transfer

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteTransferUseCase(private val transferRepository: TransferRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(portfolioId: Long, id: Long): Boolean {
        val existing = transferRepository.findById(id) ?: return false
        if (existing.portfolioId != portfolioId) return false
        log.info("Deleting transfer {} from portfolio {}", id, portfolioId)
        transferRepository.delete(id)
        return true
    }
}
