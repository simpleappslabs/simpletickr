package com.simpletickr.transfer

import com.simpletickr.transaction.model.Transaction

// Moves a quantity of one asset from one account to another, optionally crossing portfolios.
// Unlike CryptoTrade, source and destination account (and possibly portfolio) always differ,
// so there's no single "the portfolio" for the transfer itself — each leg carries its own
// portfolioId/accountId. `in` is a Kotlin keyword, hence sourceLeg/destinationLeg.
data class Transfer(
    val id: Long,
    val sourceLeg: Transaction,
    val destinationLeg: Transaction,
)
