package com.simpletickr.account.usecase

import com.simpletickr.account.UpdateAccountCommand
import com.simpletickr.account.model.Account
import com.simpletickr.account.persistence.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdateAccountUseCase(private val accountRepository: AccountRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long, command: UpdateAccountCommand): Account? {
        log.info("Updating account id={}", id)
        val existing = accountRepository.findById(id) ?: return null
        return accountRepository.update(existing.copy(
            name = command.name,
            broker = command.broker,
            accountType = command.accountType,
            currency = command.currency,
            accountNumber = command.accountNumber,
            institution = command.institution,
        ))
    }
}
