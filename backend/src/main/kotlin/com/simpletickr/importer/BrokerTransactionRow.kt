package com.simpletickr.importer

import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

data class BrokerTransactionRow(
    val lineNumber: Int,
    val externalInstrumentName: String,
    val type: TransactionType,
    val date: LocalDate,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val rawQty: String,
    val rawPrice: String,
    val currency: CurrencyCode,
)
