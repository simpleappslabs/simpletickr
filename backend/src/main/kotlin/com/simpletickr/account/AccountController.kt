package com.simpletickr.account

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.usecase.CreateAccountUseCase
import com.simpletickr.account.usecase.DeleteAccountUseCase
import com.simpletickr.account.usecase.UpdateAccountUseCase
import com.simpletickr.auth.currentUser
import com.simpletickr.generated.api.AccountsApi
import com.simpletickr.generated.model.AccountRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.Account as AccountModel
import com.simpletickr.generated.model.AccountType as GeneratedAccountType

@RestController
class AccountController(
    private val accountService: AccountService,
    private val createAccountUseCase: CreateAccountUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
) : AccountsApi {

    override fun listAccounts(): ResponseEntity<List<AccountModel>> =
        ResponseEntity.ok(accountService.listAccounts(currentUser().id).map { it.toModel() })

    override fun getAccount(id: Long): ResponseEntity<AccountModel> {
        val account = accountService.getAccount(id, currentUser().id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(account.toModel())
    }

    override fun createAccount(accountRequest: AccountRequest): ResponseEntity<AccountModel> {
        val command = CreateAccountCommand(
            name = accountRequest.name,
            broker = accountRequest.broker,
            accountType = AccountType.valueOf(accountRequest.accountType.value),
            currency = accountRequest.currency,
            accountNumber = accountRequest.accountNumber,
            institution = accountRequest.institution,
        )
        return ResponseEntity.status(201).body(createAccountUseCase.execute(command, currentUser().id).toModel())
    }

    override fun updateAccount(id: Long, accountRequest: AccountRequest): ResponseEntity<AccountModel> {
        val command = UpdateAccountCommand(
            name = accountRequest.name,
            broker = accountRequest.broker,
            accountType = AccountType.valueOf(accountRequest.accountType.value),
            currency = accountRequest.currency,
            accountNumber = accountRequest.accountNumber,
            institution = accountRequest.institution,
        )
        val account = updateAccountUseCase.execute(id, command, currentUser().id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(account.toModel())
    }

    override fun deleteAccount(id: Long): ResponseEntity<Unit> {
        if (!deleteAccountUseCase.execute(id, currentUser().id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    private fun Account.toModel() = AccountModel(
        id = id,
        name = name,
        broker = broker,
        accountType = GeneratedAccountType.valueOf(accountType.name),
        currency = currency,
        accountNumber = accountNumber,
        institution = institution,
        transactionCount = transactionCount,
    )
}
