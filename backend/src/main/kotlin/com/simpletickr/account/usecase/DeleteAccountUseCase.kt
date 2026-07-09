package com.simpletickr.account.usecase

import com.simpletickr.account.persistence.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteAccountUseCase(private val accountRepository: AccountRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long): Boolean {
        log.info("Deleting account id={}", id)
        if (accountRepository.findById(id) == null) return false
        accountRepository.delete(id)
        return true
    }
}
