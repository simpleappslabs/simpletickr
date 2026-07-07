package com.simpletickr.transaction

import com.simpletickr.transaction.model.TransactionType
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
    val broker: String? = null,
    val notes: String? = null,
)

data class RecordCryptoTradeCommand(
    val sellListingId: Long,
    val sellQuantity: BigDecimal,
    val sellPrice: BigDecimal,
    val buyListingId: Long,
    val buyQuantity: BigDecimal,
    val buyPrice: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal? = null,
    val broker: String? = null,
    val notes: String? = null,
)

data class AmendTransactionCommand(
    val listingId: Long,
    val type: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
    val fxRate: BigDecimal? = null,
    val broker: String? = null,
    val notes: String? = null,
)
