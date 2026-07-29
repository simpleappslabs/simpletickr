package com.simpletickr.user.persistence

import com.simpletickr.user.model.User
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<User> { rs, _ ->
        User(id = rs.getLong("id"), username = rs.getString("username"))
    }

    fun findById(id: Long): User? = try {
        jdbcTemplate.queryForObject("SELECT id, username FROM users WHERE id = ?", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    fun findByUsername(username: String): User? = try {
        jdbcTemplate.queryForObject("SELECT id, username FROM users WHERE username = ?", rowMapper, username)
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(username: String): User {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement("INSERT INTO users (username) VALUES (?)", arrayOf("id"))
                .apply { setString(1, username) }
        }, keyHolder)
        return User(id = keyHolder.key!!.toLong(), username = username)
    }
}
