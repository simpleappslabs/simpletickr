package com.simpletickr.fx.persistence

import com.simpletickr.fx.model.FxRate
import com.simpletickr.shared.CurrencyCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class FxRateRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<FxRate> { rs, _ ->
        FxRate(
            baseCurrency = CurrencyCode(rs.getString("base_currency")),
            quoteCurrency = CurrencyCode(rs.getString("quote_currency")),
            date = rs.getDate("date").toLocalDate(),
            rate = rs.getBigDecimal("rate"),
        )
    }

    fun upsert(rates: List<FxRate>) {
        if (rates.isEmpty()) return
        jdbcTemplate.batchUpdate(
            """INSERT INTO fx_rates (base_currency, quote_currency, date, rate)
               VALUES (?, ?, ?, ?)
               ON CONFLICT (base_currency, quote_currency, date) DO UPDATE SET rate = EXCLUDED.rate""",
            rates.map { arrayOf<Any>(it.baseCurrency.value, it.quoteCurrency.value, it.date, it.rate) }
        )
    }

    fun findLatest(baseCurrency: CurrencyCode, quoteCurrency: CurrencyCode): BigDecimal? =
        jdbcTemplate.query(
            """SELECT rate FROM fx_rates
               WHERE base_currency = ? AND quote_currency = ?
               ORDER BY date DESC LIMIT 1""",
            { rs, _ -> rs.getBigDecimal("rate") },
            baseCurrency.value, quoteCurrency.value
        ).firstOrNull()

    fun findOnDate(baseCurrency: CurrencyCode, quoteCurrency: CurrencyCode, date: LocalDate): FxRate? =
        jdbcTemplate.query(
            """SELECT base_currency, quote_currency, date, rate FROM fx_rates
               WHERE base_currency = ? AND quote_currency = ? AND date <= ?
               ORDER BY date DESC LIMIT 1""",
            rowMapper, baseCurrency.value, quoteCurrency.value, date
        ).firstOrNull()

    // The single most recent rate strictly before `date` — combined with findBetween(from, to),
    // this is everything a forward-fill over [from, to] needs: one boundary row to seed day one,
    // plus every rate actually inside the window. See AssetPriceHistoryRepository.findLatestBefore
    // for why this is deliberately not "from inception to `to`".
    fun findLatestBefore(baseCurrency: CurrencyCode, quoteCurrency: CurrencyCode, date: LocalDate): FxRate? =
        jdbcTemplate.query(
            """SELECT base_currency, quote_currency, date, rate FROM fx_rates
               WHERE base_currency = ? AND quote_currency = ? AND date < ?
               ORDER BY date DESC LIMIT 1""",
            rowMapper, baseCurrency.value, quoteCurrency.value, date
        ).firstOrNull()

    fun findBetween(baseCurrency: CurrencyCode, quoteCurrency: CurrencyCode, from: LocalDate, to: LocalDate): List<FxRate> =
        jdbcTemplate.query(
            """SELECT base_currency, quote_currency, date, rate FROM fx_rates
               WHERE base_currency = ? AND quote_currency = ? AND date BETWEEN ? AND ?
               ORDER BY date""",
            rowMapper, baseCurrency.value, quoteCurrency.value, from, to
        )
}
