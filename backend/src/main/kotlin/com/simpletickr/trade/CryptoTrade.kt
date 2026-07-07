package com.simpletickr.trade

import com.simpletickr.transaction.model.Transaction

// A crypto-to-crypto swap recorded as two ordinary SELL + BUY transactions sharing the same trade_id.
// Kept as two rows so HoldingService, ValuationService, and RealizedGainsCalculator need no changes —
// they already process BUY/SELL by sign. The CryptoTrade is the user-facing concept; the transactions
// are the portfolio-affecting artefacts. Deleting either leg cascades through the FK to remove both.
data class CryptoTrade(
    val id: Long,
    val portfolioId: Long,
    val sell: Transaction,
    val buy: Transaction,
)
