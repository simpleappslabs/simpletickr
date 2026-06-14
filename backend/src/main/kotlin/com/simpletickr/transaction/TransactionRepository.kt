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
            listingId = rs.getLong("listing_id"),
            assetId = rs.getLong("asset_id"),
            type = TransactionType.valueOf(rs.getString("type")),
            quantity = rs.getBigDecimal("quantity"),
            price = rs.getBigDecimal("price"),
            date = rs.getDate("date").toLocalDate(),
            fees = rs.getBigDecimal("fees"),
        )
    }

    private val baseSelect = """
        SELECT t.id, t.portfolio_id, t.listing_id, l.asset_id,
               t.type, t.quantity, t.price, t.date, t.fees
        FROM transactions t
        JOIN listings l ON l.id = t.listing_id
    """.trimIndent()

    fun findAll(portfolioId: Long?): List<Transaction> =
        if (portfolioId != null)
            jdbcTemplate.query("$baseSelect WHERE t.portfolio_id = ? ORDER BY t.date DESC", rowMapper, portfolioId)
        else
            jdbcTemplate.query("$baseSelect ORDER BY t.date DESC", rowMapper)

    fun findById(id: Long): Transaction? = try {
        jdbcTemplate.queryForObject("$baseSelect WHERE t.id = ?", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(transaction: Transaction): Transaction {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO transactions (portfolio_id, listing_id, type, quantity, price, date, fees) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, transaction.portfolioId)
                setLong(2, transaction.listingId)
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
            "UPDATE transactions SET listing_id = ?, type = ?, quantity = ?, price = ?, date = ?, fees = ? WHERE id = ?",
            transaction.listingId, transaction.type.name, transaction.quantity, transaction.price,
            transaction.date, transaction.fees, transaction.id
        )
        return if (updated == 0) null else transaction
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM transactions WHERE id = ?", id)
    }
}
