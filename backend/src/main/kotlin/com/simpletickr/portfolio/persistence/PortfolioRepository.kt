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
        Portfolio(rs.getLong("id"), rs.getObject("uuid", UUID::class.java), rs.getString("name"), rs.getLong("user_id"))
    }

    fun findAll(): List<Portfolio> =
        jdbcTemplate.query("SELECT id, uuid, name, user_id FROM portfolios", rowMapper)

    fun findAllForUser(userId: Long): List<Portfolio> =
        jdbcTemplate.query("SELECT id, uuid, name, user_id FROM portfolios WHERE user_id = ?", rowMapper, userId)

    fun isOwnedBy(portfolioId: Long, userId: Long): Boolean = findById(portfolioId)?.userId == userId

    fun findById(id: Long): Portfolio? = try {
        jdbcTemplate.queryForObject("SELECT id, uuid, name, user_id FROM portfolios WHERE id = ?", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    fun findByUuid(uuid: UUID): Portfolio? = try {
        jdbcTemplate.queryForObject("SELECT id, uuid, name, user_id FROM portfolios WHERE uuid = ?", rowMapper, uuid)
    } catch (_: EmptyResultDataAccessException) { null }

    fun findByIds(ids: Set<Long>): List<Portfolio> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return jdbcTemplate.query(
            "SELECT id, uuid, name, user_id FROM portfolios WHERE id IN ($placeholders)",
            rowMapper, *ids.toTypedArray()
        )
    }

    fun findByName(name: String): Portfolio? = try {
        jdbcTemplate.queryForObject("SELECT id, uuid, name, user_id FROM portfolios WHERE name = ?", rowMapper, name)
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(name: String, userId: Long, uuid: UUID = UUID.randomUUID()): Portfolio {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement("INSERT INTO portfolios (user_id, uuid, name) VALUES (?, ?, ?)", arrayOf("id"))
                .apply {
                    setLong(1, userId)
                    setObject(2, uuid)
                    setString(3, name)
                }
        }, keyHolder)

        val id = keyHolder.key!!.toLong()
        return Portfolio(id, uuid, name, userId)
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
