package com.simpletickr.portfolio.model

import com.simpletickr.shared.CurrencyCode
import java.math.BigDecimal

// Quantity held per (accountId, listingId) — the account-scoped counterpart to Holding.
// No cost basis: transfers carry no price, so cost can't be attributed to a specific account
// without inventing a lot-carry-over policy. Valuation-only (see ValuationService).
data class AccountHolding(
    val accountId: Long,
    val listingId: Long,
    val currency: CurrencyCode,
    val quantity: BigDecimal,
)
