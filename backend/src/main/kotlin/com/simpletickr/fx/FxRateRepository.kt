package com.simpletickr.fx

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class FxRateRepository(private val jdbcTemplate: JdbcTemplate) {

    fun upsert(rates: List<FxRate>) {
        if (rates.isEmpty()) return
        jdbcTemplate.batchUpdate(
            """INSERT INTO fx_rates (base_currency, quote_currency, date, rate)
               VALUES (?, ?, ?, ?)
               ON CONFLICT (base_currency, quote_currency, date) DO UPDATE SET rate = EXCLUDED.rate""",
            rates.map { arrayOf<Any>(it.baseCurrency, it.quoteCurrency, it.date, it.rate) }
        )
    }

    fun findLatest(baseCurrency: String, quoteCurrency: String): BigDecimal? =
        jdbcTemplate.query(
            """SELECT rate FROM fx_rates
               WHERE base_currency = ? AND quote_currency = ?
               ORDER BY date DESC LIMIT 1""",
            { rs, _ -> rs.getBigDecimal("rate") },
            baseCurrency, quoteCurrency
        ).firstOrNull()

    fun findOnDate(baseCurrency: String, quoteCurrency: String, date: LocalDate): BigDecimal? =
        jdbcTemplate.query(
            """SELECT rate FROM fx_rates
               WHERE base_currency = ? AND quote_currency = ? AND date <= ?
               ORDER BY date DESC LIMIT 1""",
            { rs, _ -> rs.getBigDecimal("rate") },
            baseCurrency, quoteCurrency, date
        ).firstOrNull()

    fun findDistinctQuoteCurrencies(baseCurrency: String): List<String> =
        jdbcTemplate.queryForList(
            "SELECT DISTINCT quote_currency FROM fx_rates WHERE base_currency = ?",
            String::class.java,
            baseCurrency
        )
}
