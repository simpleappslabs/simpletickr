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
                "SELECT id, portfolio_id, asset_id, type, quantity, price, date, fees FROM transactions WHERE portfolio_id = ? ORDER BY date desc",
                rowMapper, portfolioId
            )
        else
            jdbcTemplate.query(
                "SELECT id, portfolio_id, asset_id, type, quantity, price, date, fees FROM transactions ORDER BY date desc",
                rowMapper
            )

    fun findById(id: Long): Transaction? = try {
        jdbcTemplate.queryForObject(
            "SELECT id, portfolio_id, asset_id, type, quantity, price, date, fees FROM transactions WHERE id = ?",
            rowMapper, id
        )
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(transaction: Transaction): Transaction {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO transactions (portfolio_id, asset_id, type, quantity, price, date, fees) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, transaction.portfolioId)
                setLong(2, transaction.assetId)
                setString(3, transaction.type.name)
                setBigDecimal(4, transaction.quantity)
                setBigDecimal(5, transaction.price)
                setObject(6, transaction.date)
                setBigDecimal(7, transaction.fees)
            }
        }, keyHolder)
        return transaction.copy(id = keyHolder.key!!.toLong())
    }

    fun update(transaction: Transaction): Transaction? {
        val updated = jdbcTemplate.update(
            "UPDATE transactions SET asset_id = ?, type = ?, quantity = ?, price = ?, date = ?, fees = ? WHERE id = ?",
            transaction.assetId, transaction.type.name, transaction.quantity, transaction.price,
            transaction.date, transaction.fees, transaction.id
        )
        return if (updated == 0) null else transaction
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM transactions WHERE id = ?", id)
    }
}
