package com.simpletickr.asset

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class AssetRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Asset> { rs, _ ->
        Asset(
            id = rs.getLong("id"),
            ticker = rs.getString("ticker"),
            name = rs.getString("name"),
            type = AssetType.valueOf(rs.getString("type")),
            currency = rs.getString("currency"),
            currentPrice = rs.getBigDecimal("current_price"),
        )
    }

    fun findAll(): List<Asset> =
        jdbcTemplate.query("SELECT id, ticker, name, type, currency, current_price FROM assets", rowMapper)

    fun findById(id: Long): Asset? = try {
        jdbcTemplate.queryForObject(
            "SELECT id, ticker, name, type, currency, current_price FROM assets WHERE id = ?",
            rowMapper, id
        )
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(ticker: String, name: String, type: AssetType, currency: String, currentPrice: BigDecimal?): Asset {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO assets (ticker, name, type, currency, current_price) VALUES (?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setString(1, ticker)
                setString(2, name)
                setString(3, type.name)
                setString(4, currency)
                setBigDecimal(5, currentPrice)
            }
        }, keyHolder)
        return Asset(keyHolder.key!!.toLong(), ticker, name, type, currency, currentPrice)
    }

    fun update(id: Long, ticker: String, name: String, type: AssetType, currency: String, currentPrice: BigDecimal?): Asset? {
        val rows = jdbcTemplate.update(
            "UPDATE assets SET ticker = ?, name = ?, type = ?, currency = ?, current_price = ? WHERE id = ?",
            ticker, name, type.name, currency, currentPrice, id
        )
        return if (rows == 0) null else findById(id)
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM assets WHERE id = ?", id)
    }
}