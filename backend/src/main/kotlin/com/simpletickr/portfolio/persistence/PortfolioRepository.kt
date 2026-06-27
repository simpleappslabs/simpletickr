package com.simpletickr.portfolio.persistence

import com.simpletickr.portfolio.model.Portfolio
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class PortfolioRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Portfolio> { rs, _ ->
        Portfolio(rs.getLong("id"), rs.getString("name"))
    }

    fun findAll(): List<Portfolio> =
        jdbcTemplate.query("SELECT id, name FROM portfolios", rowMapper)

    fun findById(id: Long): Portfolio? = try {
        jdbcTemplate.queryForObject("SELECT id, name FROM portfolios WHERE id = ?", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

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
