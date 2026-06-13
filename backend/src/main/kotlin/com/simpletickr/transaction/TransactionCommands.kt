package com.simpletickr.transaction

import java.math.BigDecimal
import java.time.LocalDate

data class RecordTransactionCommand(
    val assetId: Long,
    val type: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
)

data class AmendTransactionCommand(
    val assetId: Long,
    val type: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
)
