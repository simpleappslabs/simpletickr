package com.simpletickr.transaction

import java.math.BigDecimal
import java.time.LocalDate

data class RecordTransactionCommand(
    val listingId: Long,
    val type: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
    val fxRate: BigDecimal? = null,
    val externalId: String? = null,
)

data class AmendTransactionCommand(
    val listingId: Long,
    val type: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
    val fxRate: BigDecimal? = null,
)
