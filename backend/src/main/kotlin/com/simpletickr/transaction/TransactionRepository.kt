package com.simpletickr.transaction

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class TransactionRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Transaction> { rs, _ ->
        Transaction(
            id = rs.getLong("id"),
            portfolioId = rs.getLong("portfolio_id"),
            assetId = rs.getLong("asset_id"),
            type = TransactionType.valueOf(rs.getString("type")),
            quantity = rs.getBigDecimal("quantity"),
            price = rs.getBigDecimal("price"),
            date = rs.getDate("date").toLocalDate(),
            fees = rs.getBigDecimal("fees"),
        )
    }

    fun findAll(portfolioId: Long?): List<Transaction> =
        if (portfolioId != null)
            jdbcTemplate.query(
                "SELECT id, portfolio_id, asset_id, type, quantity, price, date, fees FROM transactions WHERE portfolio_id = ?",
                rowMapper, portfolioId
            )
        else
            jdbcTemplate.query(
                "SELECT id, portfolio_id, asset_id, type, quantity, price, date, fees FROM transactions",
                rowMapper
            )

    fun findById(id: Long): Transaction? = try {
        jdbcTemplate.queryForObject(
            "SELECT id, portfolio_id, asset_id, type, quantity, price, date, fees FROM transactions WHERE id = ?",
            rowMapper, id
        )
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(
        portfolioId: Long,
        assetId: Long,
        type: TransactionType,
        quantity: BigDecimal,
        price: BigDecimal,
        date: LocalDate,
        fees: BigDecimal?,
    ): Transaction {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO transactions (portfolio_id, asset_id, type, quantity, price, date, fees) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, portfolioId)
                setLong(2, assetId)
                setString(3, type.name)
                setBigDecimal(4, quantity)
                setBigDecimal(5, price)
                setObject(6, date)
                setBigDecimal(7, fees)
            }
        }, keyHolder)
        return Transaction(keyHolder.key!!.toLong(), portfolioId, assetId, type, quantity, price, date, fees)
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM transactions WHERE id = ?", id)
    }
}