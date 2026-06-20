package com.simpletickr.fx

import com.simpletickr.shared.CurrencyCode
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
            { rs, _ -> FxRate(
                baseCurrency = CurrencyCode(rs.getString("base_currency")),
                quoteCurrency = CurrencyCode(rs.getString("quote_currency")),
                date = rs.getDate("date").toLocalDate(),
                rate = rs.getBigDecimal("rate"),
            )},
            baseCurrency.value, quoteCurrency.value, date
        ).firstOrNull()

    fun findDistinctQuoteCurrencies(baseCurrency: CurrencyCode): List<CurrencyCode> =
        jdbcTemplate.queryForList(
            "SELECT DISTINCT quote_currency FROM fx_rates WHERE base_currency = ?",
            String::class.java,
            baseCurrency.value
        ).map { CurrencyCode(it) }
}
