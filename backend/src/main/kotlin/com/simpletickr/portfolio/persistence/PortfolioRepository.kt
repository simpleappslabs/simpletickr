package com.simpletickr.portfolio.persistence

import com.simpletickr.portfolio.model.Portfolio
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PortfolioRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Portfolio> { rs, _ ->
        Portfolio(rs.getLong("id"), rs.getObject("uuid", UUID::class.java), rs.getString("name"))
    }

    fun findAll(): List<Portfolio> =
        jdbcTemplate.query("SELECT id, uuid, name FROM portfolios", rowMapper)

    fun findById(id: Long): Portfolio? = try {
        jdbcTemplate.queryForObject("SELECT id, uuid, name FROM portfolios WHERE id = ?", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    fun findByUuid(uuid: UUID): Portfolio? = try {
        jdbcTemplate.queryForObject("SELECT id, uuid, name FROM portfolios WHERE uuid = ?", rowMapper, uuid)
    } catch (_: EmptyResultDataAccessException) { null }

    fun findByName(name: String): Portfolio? = try {
        jdbcTemplate.queryForObject("SELECT id, uuid, name FROM portfolios WHERE name = ?", rowMapper, name)
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(name: String, uuid: UUID = UUID.randomUUID()): Portfolio {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement("INSERT INTO portfolios (user_id, uuid, name) VALUES (?, ?, ?)", arrayOf("id"))
                .apply {
                    setLong(1, 1L)
                    setObject(2, uuid)
                    setString(3, name)
                }
        }, keyHolder)

        val id = keyHolder.key!!.toLong()
        return Portfolio(id, uuid, name)
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
