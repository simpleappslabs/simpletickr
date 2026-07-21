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
    val accountId: Long,
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
    val accountId: Long,
    val notes: String? = null,
)

data class RecordTransferCommand(
    val listingId: Long,
    val quantity: BigDecimal,
    // In-kind fee (e.g. crypto gas), deducted from the quantity received at the destination.
    // Fiat/cash transfer fees are not modeled — see Transaction.assetFeeQuantity.
    val assetFeeQuantity: BigDecimal? = null,
    val date: LocalDate,
    val sourceAccountId: Long,
    val destinationAccountId: Long,
    val destinationPortfolioId: Long,
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
    val accountId: Long,
    val notes: String? = null,
)
