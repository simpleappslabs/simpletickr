package com.simpletickr.portfolio.persistence

import com.simpletickr.portfolio.model.PortfolioValuePoint
import com.simpletickr.shared.CurrencyCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class PortfolioValueHistoryRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findOldestTransactionDate(portfolioId: Long): LocalDate? =
        jdbcTemplate.query(
            "SELECT MIN(date) FROM transactions WHERE portfolio_id = ?",
            { rs, _ -> rs.getDate(1)?.toLocalDate() },
            portfolioId,
        ).firstOrNull()

    fun findValueHistory(
        portfolioId: Long,
        baseCurrency: CurrencyCode,
        from: LocalDate,
        to: LocalDate,
    ): List<PortfolioValuePoint> = jdbcTemplate.query(
        SQL,
        { rs, _ ->
            PortfolioValuePoint(
                date = rs.getDate("date").toLocalDate(),
                value = rs.getBigDecimal("total_value"),
                invested = rs.getBigDecimal("total_invested"),
            )
        },
        from, to,
        portfolioId,
        portfolioId,
        baseCurrency.value, baseCurrency.value, baseCurrency.value,
        baseCurrency.value, portfolioId, baseCurrency.value,
    )

    companion object {
        private val SQL = """
            WITH date_series AS (
                -- One row per calendar day in the requested range
                SELECT generate_series(?::date, ?::date, '1 day'::interval)::date AS d
            ),
            -- Step 1: collapse multiple transactions on the same (listing, date) into a single net delta.
            -- SPLIT transactions are excluded — they don't change the cash position.
            -- Separating this from the window function below makes the two-step intent explicit.
            daily_changes AS (
                SELECT
                    listing_id,
                    date,
                    SUM(CASE type WHEN 'BUY' THEN quantity ELSE -quantity END) AS delta
                FROM transactions
                WHERE portfolio_id = ? AND type IN ('BUY', 'SELL')
                GROUP BY listing_id, date
            ),
            -- Collect split events for this portfolio so we can adjust historical quantities.
            splits_raw AS (
                SELECT listing_id, date, quantity AS ratio
                FROM transactions
                WHERE portfolio_id = ? AND type = 'SPLIT'
            ),
            -- Step 2: running net quantity per listing at each transaction date.
            -- Computing this once with a window function avoids the O(dates × transactions) cost
            -- of joining date_series directly to transactions for every date.
            position_snapshots AS (
                SELECT
                    listing_id,
                    date,
                    SUM(delta) OVER (PARTITION BY listing_id ORDER BY date) AS net_qty_raw
                FROM daily_changes
            ),
            -- Apply the forward-looking split multiplier: for a snapshot at date D, multiply net_qty
            -- by the product of all split ratios occurring AFTER D for the same listing.
            -- Yahoo Finance retroactively adjusts historical prices for splits, so using the
            -- split-adjusted quantity keeps value = net_qty × close_price correct at all dates.
            split_adjusted_snapshots AS (
                SELECT
                    listing_id,
                    date,
                    net_qty_raw * COALESCE(
                        (SELECT EXP(SUM(LN(ratio::float8)))
                         FROM splits_raw sr
                         WHERE sr.listing_id = position_snapshots.listing_id
                           AND sr.date > position_snapshots.date),
                        1.0
                    ) AS net_qty
                FROM position_snapshots
            ),
            portfolio_listings AS (
                SELECT DISTINCT listing_id FROM split_adjusted_snapshots
            ),
            -- Forward-fill net quantity from transaction dates to every calendar date.
            -- DISTINCT ON + ORDER BY date DESC picks the most recent snapshot on or before each day.
            -- NULLS LAST handles dates before the first transaction (ps.date IS NULL → net_qty IS NULL).
            date_positions AS (
                SELECT DISTINCT ON (ds.d, pl.listing_id)
                    ds.d AS date,
                    pl.listing_id,
                    ps.net_qty
                FROM date_series ds
                CROSS JOIN portfolio_listings pl
                LEFT JOIN split_adjusted_snapshots ps
                    ON ps.listing_id = pl.listing_id AND ps.date <= ds.d
                ORDER BY ds.d, pl.listing_id, ps.date DESC NULLS LAST
            ),
            -- Attach the most recent closing price on or before each date (prices carry forward over weekends/holidays).
            -- Filters to open positions only (net_qty > 0); fully closed positions are excluded.
            open_with_price AS (
                SELECT DISTINCT ON (dp.date, dp.listing_id)
                    dp.date,
                    dp.listing_id,
                    dp.net_qty,
                    l.currency,
                    aph.close_price
                FROM date_positions dp
                JOIN listings l ON l.id = dp.listing_id
                LEFT JOIN asset_price_history aph
                    ON aph.listing_id = dp.listing_id AND aph.date <= dp.date
                WHERE dp.net_qty > 0
                ORDER BY dp.date, dp.listing_id, aph.date DESC NULLS LAST
            ),
            -- Attach the most recent FX rate on or before each date.
            -- FX convention: 1 baseCurrency = rate quoteCurrency, so value_base = value_local / rate.
            -- Same-currency listings get fx_rate = 1 (no join needed).
            with_fx AS (
                SELECT DISTINCT ON (p.date, p.listing_id)
                    p.date,
                    p.listing_id,
                    p.net_qty,
                    p.close_price,
                    p.currency,
                    CASE WHEN p.currency = ? THEN CAST(1.0 AS numeric(19,8))
                         ELSE fr.rate END AS fx_rate
                FROM open_with_price p
                LEFT JOIN fx_rates fr
                    ON fr.base_currency = ?
                    AND fr.quote_currency = p.currency
                    AND p.currency != ?
                    AND fr.date <= p.date
                ORDER BY p.date, p.listing_id, fr.date DESC NULLS LAST
            ),
            -- Net cash deployed per day: BUY cost minus SELL proceeds, converted to base currency
            -- using the FX rate recorded on the transaction itself (not the current rate).
            -- Skips transactions where fx_rate IS NULL and currency != base — should not happen in
            -- practice since the system auto-fetches FX rates at record time.
            daily_invested_changes AS (
                SELECT
                    t.date,
                    SUM(
                        CASE t.type WHEN 'BUY' THEN 1 ELSE -1 END
                        * t.quantity * t.price
                        / CASE WHEN l.currency = ? THEN CAST(1.0 AS numeric(19,8))
                               ELSE t.fx_rate END
                    ) AS delta
                FROM transactions t
                JOIN listings l ON l.id = t.listing_id
                WHERE t.portfolio_id = ?
                  AND t.type IN ('BUY', 'SELL')
                  AND (l.currency = ? OR t.fx_rate IS NOT NULL)
                GROUP BY t.date
            ),
            cumulative_invested AS (
                SELECT date, SUM(delta) OVER (ORDER BY date) AS invested
                FROM daily_invested_changes
            )
            SELECT
                ds.d AS date,
                -- Partial sum: holdings missing a price or FX rate on this date are excluded from
                -- the total rather than nulling out the whole day. SUM(...) FILTER(...) already
                -- returns NULL when zero rows match (whether because there are no open positions
                -- at all, or because none of them are priced) — no separate CASE needed for that.
                SUM(fx.net_qty * fx.close_price / fx.fx_rate)
                    FILTER (WHERE fx.close_price IS NOT NULL AND fx.fx_rate IS NOT NULL) AS total_value,
                -- Invested only changes on transaction dates; forward-fill it to every calendar day.
                (SELECT ci.invested
                 FROM cumulative_invested ci
                 WHERE ci.date <= ds.d
                 ORDER BY ci.date DESC
                 LIMIT 1) AS total_invested
            FROM date_series ds
            LEFT JOIN with_fx fx ON fx.date = ds.d
            GROUP BY ds.d
            ORDER BY ds.d
        """.trimIndent()
    }
}
