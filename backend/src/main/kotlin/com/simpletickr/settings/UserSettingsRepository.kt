package com.simpletickr.settings

import com.simpletickr.shared.CurrencyCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class UserSettingsRepository(private val jdbcTemplate: JdbcTemplate) {

    fun find(): UserSettings =
        jdbcTemplate.query(
            "SELECT base_currency FROM user_settings WHERE id = 1",
            { rs, _ -> UserSettings(CurrencyCode(rs.getString("base_currency"))) }
        ).firstOrNull() ?: UserSettings(CurrencyCode("EUR"))

    fun update(settings: UserSettings) {
        jdbcTemplate.update(
            "UPDATE user_settings SET base_currency = ? WHERE id = 1",
            settings.baseCurrency.value
        )
    }
}
