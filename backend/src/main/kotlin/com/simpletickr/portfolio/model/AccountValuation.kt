package com.simpletickr.portfolio.model

import java.math.BigDecimal

// Current market value held in a single account, in baseCurrency. No cost basis — see AccountHolding.
data class AccountValuation(
    val accountId: Long,
    val marketValueBase: BigDecimal?,
)
