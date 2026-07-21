package com.simpletickr.transfer

import java.math.BigDecimal
import java.time.LocalDate

data class RecordTransferCommand(
    val listingId: Long,
    val quantity: BigDecimal,
    // In-kind fee (e.g. crypto gas). Fiat/cash transfer fees are not modeled — see Transfer.assetFeeQuantity.
    val assetFeeQuantity: BigDecimal? = null,
    val date: LocalDate,
    val sourceAccountId: Long,
    val destinationAccountId: Long,
    val notes: String? = null,
)
