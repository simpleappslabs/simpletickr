package com.simpletickr.trade

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class CryptoTradeRepository(private val jdbcTemplate: JdbcTemplate) {

    fun create(portfolioId: Long): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO crypto_trades (portfolio_id) VALUES (?)",
                arrayOf("id")
            ).apply { setLong(1, portfolioId) }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM crypto_trades WHERE id = ?", id)
    }
}
