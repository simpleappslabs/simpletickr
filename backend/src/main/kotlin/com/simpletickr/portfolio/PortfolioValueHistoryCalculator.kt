package com.simpletickr.portfolio

import com.simpletickr.asset.model.Listing
import com.simpletickr.fx.model.FxRate
import com.simpletickr.portfolio.model.PortfolioValuePoint
import com.simpletickr.price.model.PricePoint
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.SplitAdjuster
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// Pure replay of a portfolio's transaction history into one (value, invested) point per calendar
// day. Same replay style as HoldingService, but forward-filled across every day in the requested
// range instead of collapsed to a single "now" snapshot, and priced/FX-converted per day using
// each day's own latest available price/rate (prices and rates carry forward over gaps, same as
// a "last observation carried forward" chart).
//
// Deliberately fetches whole time series (transactions, price history, FX history) and folds them
// in a handful of single forward passes, rather than doing per-day "as of" lookups — each pass is
// O(days + points) instead of O(days × points), and the whole computation stays in Kotlin instead
// of a database round trip per lookup.
object PortfolioValueHistoryCalculator {

    fun compute(
        transactions: List<Transaction>,
        listingMap: Map<Long, Listing>,
        priceHistory: Map<Long, List<PricePoint>>,
        fxRateHistory: Map<CurrencyCode, List<FxRate>>,
        baseCurrency: CurrencyCode,
        from: LocalDate,
        to: LocalDate,
    ): List<PortfolioValuePoint> {
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

        // Splits apply uniformly regardless of which day/listing combination is being valued —
        // same splitIndex shape and lookup as HoldingService.
        val splitIndex = transactions
            .filter { it.type == TransactionType.SPLIT }
            .groupBy { it.listingId }
            .mapValues { (_, splits) -> splits.map { it.date to it.quantity } }

        val netQtyByListing = netQuantitiesByListing(transactions, splitIndex, dates)
        val priceByListing = priceHistory.mapValues { (_, points) -> forwardFill(points.map { it.date to it.price }, dates) }
        val fxByCurrency = fxRateHistory.mapValues { (_, rates) -> forwardFill(rates.map { it.date to it.rate }, dates) }
        val investedByDate = cumulativeInvested(transactions, listingMap, baseCurrency, dates)

        return dates.map { date ->
            // Partial sum: a listing missing a price or FX rate on this day is excluded from the
            // day's total rather than nulling the whole day out — same trade-off as
            // PortfolioValuationCalculator.partialSum, just applied per calendar day here.
            val contributions = netQtyByListing.mapNotNull { (listingId, qtyByDate) ->
                val qty = qtyByDate[date] ?: return@mapNotNull null
                if (qty <= BigDecimal.ZERO) return@mapNotNull null
                val listing = listingMap[listingId] ?: return@mapNotNull null
                val price = priceByListing[listingId]?.get(date) ?: return@mapNotNull null
                val fx = if (listing.currency == baseCurrency) BigDecimal.ONE
                         else fxByCurrency[listing.currency]?.get(date) ?: return@mapNotNull null
                qty * price.divide(fx, 10, RoundingMode.HALF_UP)
            }
            PortfolioValuePoint(
                date = date,
                value = if (contributions.isEmpty()) null else contributions.reduce(BigDecimal::add),
                invested = investedByDate[date],
            )
        }
    }

    // Forward-fill: for each requested date, the most recent (date, value) on or before it.
    // `points` and `dates` are both sorted ascending, so one pointer walked forward across
    // `points` — advanced at most once per date — replaces what a per-date lookup would cost.
    private fun forwardFill(points: List<Pair<LocalDate, BigDecimal>>, dates: List<LocalDate>): Map<LocalDate, BigDecimal> {
        val sorted = points.sortedBy { it.first }
        val result = LinkedHashMap<LocalDate, BigDecimal>(dates.size)
        var i = 0
        var current: BigDecimal? = null
        for (date in dates) {
            while (i < sorted.size && !sorted[i].first.isAfter(date)) {
                current = sorted[i].second
                i++
            }
            current?.let { result[date] = it }
        }
        return result
    }

    // Net quantity per listing, forward-filled to every requested day. Multiple same-day BUY/SELL
    // transactions collapse into one net delta before the running sum, same as HoldingService's
    // daily_changes step. Each snapshot is adjusted for every split occurring after it, since
    // historical prices are themselves retroactively split-adjusted by the price provider.
    private fun netQuantitiesByListing(
        transactions: List<Transaction>,
        splitIndex: Map<Long, List<Pair<LocalDate, BigDecimal>>>,
        dates: List<LocalDate>,
    ): Map<Long, Map<LocalDate, BigDecimal>> =
        transactions
            .filter { it.type != TransactionType.SPLIT }
            .groupBy { it.listingId }
            .mapValues { (listingId, txs) ->
                val dailyDeltas = txs.groupBy { it.date }
                    .mapValues { (_, dayTxs) -> dayTxs.sumOf { if (it.type == TransactionType.BUY) it.quantity else -it.quantity } }

                var running = BigDecimal.ZERO
                val snapshots = dailyDeltas.entries.sortedBy { it.key }.map { (date, delta) ->
                    running += delta
                    val multiplier = SplitAdjuster.adjustmentFor(listingId, date, splitIndex).multiplier
                    date to (running * multiplier)
                }
                forwardFill(snapshots, dates)
            }

    // Net cash deployed, forward-filled to every requested day. Converted to base currency using
    // the FX rate recorded on the transaction itself (not the day's rate) — invested capital is a
    // historical fact, not something that moves with today's exchange rate. A transaction in a
    // non-base currency with no recorded FX rate is skipped, same as the original SQL's filter —
    // shouldn't happen in practice since FX rates are auto-fetched at record time.
    private fun cumulativeInvested(
        transactions: List<Transaction>,
        listingMap: Map<Long, Listing>,
        baseCurrency: CurrencyCode,
        dates: List<LocalDate>,
    ): Map<LocalDate, BigDecimal> {
        val dailyDeltas = transactions
            .filter { it.type == TransactionType.BUY || it.type == TransactionType.SELL }
            .mapNotNull { tx ->
                val currency = listingMap[tx.listingId]?.currency ?: return@mapNotNull null
                val fx = if (currency == baseCurrency) BigDecimal.ONE else (tx.fxRate ?: return@mapNotNull null)
                val sign = if (tx.type == TransactionType.BUY) BigDecimal.ONE else BigDecimal.ONE.negate()
                tx.date to sign * tx.quantity * tx.price.divide(fx, 10, RoundingMode.HALF_UP)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, deltas) -> deltas.reduce(BigDecimal::add) }

        var running = BigDecimal.ZERO
        val snapshots = dailyDeltas.entries.sortedBy { it.key }.map { (date, delta) ->
            running += delta
            date to running
        }
        return forwardFill(snapshots, dates)
    }
}
