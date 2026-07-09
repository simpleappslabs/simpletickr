package com.simpletickr.account.usecase

import com.simpletickr.account.CreateAccountCommand
import com.simpletickr.account.model.Account
import com.simpletickr.account.persistence.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CreateAccountUseCase(private val accountRepository: AccountRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: CreateAccountCommand): Account {
        log.info("Creating account: name={}, type={}", command.name, command.accountType)
        return accountRepository.save(Account(
            id = 0L,
            name = command.name,
            broker = command.broker,
            accountType = command.accountType,
            currency = command.currency,
            accountNumber = command.accountNumber,
            institution = command.institution,
        ))
    }
}
