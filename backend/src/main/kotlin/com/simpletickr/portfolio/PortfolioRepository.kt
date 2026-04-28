package com.simpletickr.portfolio

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class PortfolioRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findAll(): List<Portfolio> =
        jdbcTemplate.query("SELECT id, name FROM portfolios") { rs, _ ->
            Portfolio(rs.getLong("id"), rs.getString("name"))
        }

    fun findById(id: Long): Portfolio? {
        return try {
            jdbcTemplate.queryForObject("SELECT id, name FROM portfolios WHERE id = ?", { rs, _ ->
                Portfolio(rs.getLong("id"), rs.getString("name"))
            }, id)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    fun save(name: String): Portfolio {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement("INSERT INTO portfolios (user_id, name) VALUES (?, ?)", arrayOf("id"))
                .apply {
                    setLong(1, 1L)
                    setString(2, name)
                }
        }, keyHolder)

        val id = keyHolder.key!!.toLong()
        return Portfolio(id, name)
    }

    fun update(id: Long, name: String): Portfolio? {
        val rows = jdbcTemplate.update("UPDATE portfolios SET name = ? WHERE id = ?", name, id)
        if (rows == 0) return null
        return findById(id)
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM portfolios WHERE id = ?", id)
    }
}