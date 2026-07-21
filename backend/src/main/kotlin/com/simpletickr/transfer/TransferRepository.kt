package com.simpletickr.transfer

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class TransferRepository(private val jdbcTemplate: JdbcTemplate) {

    fun create(): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement("INSERT INTO transfers DEFAULT VALUES", arrayOf("id"))
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM transfers WHERE id = ?", id)
    }
}
