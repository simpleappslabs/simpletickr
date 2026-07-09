package com.simpletickr.account

import com.simpletickr.account.model.AccountType

data class CreateAccountCommand(
    val name: String,
    val broker: String?,
    val accountType: AccountType,
    val currency: String?,
    val accountNumber: String?,
    val institution: String?,
)

data class UpdateAccountCommand(
    val name: String,
    val broker: String?,
    val accountType: AccountType,
    val currency: String?,
    val accountNumber: String?,
    val institution: String?,
)
