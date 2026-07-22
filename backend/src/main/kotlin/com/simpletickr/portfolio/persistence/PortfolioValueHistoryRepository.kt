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
            -- MATERIALIZED: read twice below (portfolio_listings and date_positions); this is
            -- small (bounded by transaction count) so materializing it once is cheap and pins down
            -- the plan regardless of the planner's default inlining heuristics.
            split_adjusted_snapshots AS MATERIALIZED (
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
            -- Forward-fill net quantity from transaction dates to every calendar date. A LATERAL
            -- join picks, per (date, listing), the single most recent snapshot on or before that
            -- date — instead of joining every date × snapshot combination on an inequality and
            -- then de-duplicating (DISTINCT ON), which forces Postgres to materialize the full
            -- cross product before it can discard anything.
            date_positions AS (
                SELECT
                    ds.d AS date,
                    pl.listing_id,
                    ps.net_qty
                FROM date_series ds
                CROSS JOIN portfolio_listings pl
                LEFT JOIN LATERAL (
                    SELECT s.net_qty
                    FROM split_adjusted_snapshots s
                    WHERE s.listing_id = pl.listing_id AND s.date <= ds.d
                    ORDER BY s.date DESC
                    LIMIT 1
                ) ps ON true
            ),
            -- Attach the most recent closing price on or before each date (prices carry forward over
            -- weekends/holidays). Same LATERAL "latest as of" idiom as date_positions above — here it
            -- also lets Postgres use idx_price_history_listing_date (listing_id, date DESC) to seek
            -- straight to the right row instead of scanning the listing's whole price history for
            -- every date, which is what made this join scale with days × price points before.
            -- Filters to open positions only (net_qty > 0); fully closed positions are excluded.
            open_with_price AS (
                SELECT
                    dp.date,
                    dp.listing_id,
                    dp.net_qty,
                    l.currency,
                    aph.close_price
                FROM date_positions dp
                JOIN listings l ON l.id = dp.listing_id
                LEFT JOIN LATERAL (
                    SELECT a.close_price
                    FROM asset_price_history a
                    WHERE a.listing_id = dp.listing_id AND a.date <= dp.date
                    ORDER BY a.date DESC
                    LIMIT 1
                ) aph ON true
                WHERE dp.net_qty > 0
            ),
            -- Attach the most recent FX rate on or before each date, the same way — LATERAL join
            -- backed by idx_fx_rates_pair_date (base_currency, quote_currency, date DESC).
            -- FX convention: 1 baseCurrency = rate quoteCurrency, so value_base = value_local / rate.
            -- Same-currency listings get fx_rate = 1 (the LATERAL join is skipped via the ON clause).
            with_fx AS (
                SELECT
                    p.date,
                    p.listing_id,
                    p.net_qty,
                    p.close_price,
                    p.currency,
                    CASE WHEN p.currency = ? THEN CAST(1.0 AS numeric(19,8))
                         ELSE fr.rate END AS fx_rate
                FROM open_with_price p
                LEFT JOIN LATERAL (
                    SELECT f.rate
                    FROM fx_rates f
                    WHERE f.base_currency = ? AND f.quote_currency = p.currency AND f.date <= p.date
                    ORDER BY f.date DESC
                    LIMIT 1
                ) fr ON p.currency <> ?
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
            -- MATERIALIZED is required here, not just a hint: this CTE is referenced once, from
            -- inside the LATERAL subquery below. Without forcing materialization, Postgres inlines
            -- it into that subquery and — since it's correlated on ds.d — reruns the whole
            -- aggregation (GroupAggregate + WindowAgg over every BUY/SELL) once per calendar day.
            -- That's what previously made a 1-month range take seconds and a multi-year range never
            -- finish. Materializing forces it to be computed exactly once and reused as a small
            -- lookup table (bounded by distinct transaction dates, not by the requested range).
            cumulative_invested AS MATERIALIZED (
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
                -- MAX() collapses the (harmless) repeated lookup across however many listings this
                -- date fanned out to via the with_fx join — every one of those rows carries the same
                -- ci.invested, since it depends only on ds.d.
                MAX(ci.invested) AS total_invested
            FROM date_series ds
            LEFT JOIN with_fx fx ON fx.date = ds.d
            LEFT JOIN LATERAL (
                SELECT c.invested
                FROM cumulative_invested c
                WHERE c.date <= ds.d
                ORDER BY c.date DESC
                LIMIT 1
            ) ci ON true
            GROUP BY ds.d
            ORDER BY ds.d
        """.trimIndent()
    }
}
