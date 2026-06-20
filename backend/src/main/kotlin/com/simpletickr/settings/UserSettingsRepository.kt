package com.simpletickr.settings

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class UserSettingsRepository(private val jdbcTemplate: JdbcTemplate) {

    fun find(): UserSettings =
        jdbcTemplate.query(
            "SELECT base_currency FROM user_settings WHERE id = 1",
            { rs, _ -> UserSettings(rs.getString("base_currency")) }
        ).firstOrNull() ?: UserSettings("EUR")

    fun update(settings: UserSettings) {
        jdbcTemplate.update(
            "UPDATE user_settings SET base_currency = ? WHERE id = 1",
            settings.baseCurrency
        )
    }
}
