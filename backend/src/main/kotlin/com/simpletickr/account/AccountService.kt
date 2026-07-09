package com.simpletickr.account

import com.simpletickr.account.model.Account
import com.simpletickr.account.persistence.AccountRepository
import org.springframework.stereotype.Service

@Service
class AccountService(private val accountRepository: AccountRepository) {

    fun listAccounts(): List<Account> = accountRepository.findAll()

    fun getAccount(id: Long): Account? = accountRepository.findById(id)
}
