package com.simpletickr.settings

import com.simpletickr.shared.CurrencyCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class UserSettingsRepository(private val jdbcTemplate: JdbcTemplate) {

    fun find(userId: Long): UserSettings =
        jdbcTemplate.query(
            "SELECT base_currency FROM user_settings WHERE user_id = ?",
            { rs, _ -> UserSettings(CurrencyCode(rs.getString("base_currency"))) },
            userId,
        ).firstOrNull() ?: UserSettings(CurrencyCode("EUR"))

    fun update(userId: Long, settings: UserSettings) {
        jdbcTemplate.update(
            """
            INSERT INTO user_settings (user_id, base_currency) VALUES (?, ?)
            ON CONFLICT (user_id) DO UPDATE SET base_currency = EXCLUDED.base_currency
            """.trimIndent(),
            userId, settings.baseCurrency.value,
        )
    }

    fun findAllDistinctBaseCurrencies(): List<CurrencyCode> =
        jdbcTemplate.query(
            "SELECT DISTINCT base_currency FROM user_settings",
            { rs, _ -> CurrencyCode(rs.getString("base_currency")) },
        )
}
