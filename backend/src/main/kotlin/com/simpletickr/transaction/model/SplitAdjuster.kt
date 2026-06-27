package com.simpletickr.transaction.model

import java.math.BigDecimal
import java.time.LocalDate

object SplitAdjuster {

    data class SplitAdjustment(val multiplier: BigDecimal)

    // Returns the product of all split ratios occurring strictly after txDate,
    // for the asset/listing identified by id in splitIndex.
    // Callers build splitIndex from their own data type, keyed by listingId or assetId.
    fun adjustmentFor(
        id: Long,
        txDate: LocalDate,
        splitIndex: Map<Long, List<Pair<LocalDate, BigDecimal>>>,
    ): SplitAdjustment {
        val multiplier = splitIndex[id]
            ?.filter { (splitDate, _) -> splitDate > txDate }
            ?.fold(BigDecimal.ONE) { acc, (_, ratio) -> acc * ratio }
            ?: BigDecimal.ONE
        return SplitAdjustment(multiplier)
    }
}
