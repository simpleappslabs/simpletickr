package com.simpletickr.account.model

data class Account(
    val id: Long,
    val name: String,
    val broker: String?,
    val accountType: AccountType,
    val currency: String?,
    val accountNumber: String?,
    val institution: String?,
    val transactionCount: Long = 0,
)
