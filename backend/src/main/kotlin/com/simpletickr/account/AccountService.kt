package com.simpletickr.account

import com.simpletickr.account.model.Account
import com.simpletickr.account.persistence.AccountRepository
import org.springframework.stereotype.Service

@Service
class AccountService(private val accountRepository: AccountRepository) {

    fun listAccounts(userId: Long): List<Account> = accountRepository.findAllForUser(userId)

    fun getAccount(id: Long, userId: Long): Account? =
        accountRepository.findById(id)?.takeIf { it.userId == userId }

    fun isOwnedBy(id: Long, userId: Long): Boolean = accountRepository.isOwnedBy(id, userId)
}
