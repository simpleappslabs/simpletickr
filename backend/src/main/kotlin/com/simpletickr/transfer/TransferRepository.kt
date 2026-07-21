package com.simpletickr.transfer

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class TransferRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Transfer> { rs, _ ->
        Transfer(
            id = rs.getLong("id"),
            portfolioId = rs.getLong("portfolio_id"),
            listingId = rs.getLong("listing_id"),
            assetId = rs.getLong("asset_id"),
            quantity = rs.getBigDecimal("quantity"),
            assetFeeQuantity = rs.getBigDecimal("asset_fee_quantity"),
            date = rs.getDate("date").toLocalDate(),
            sourceAccountId = rs.getLong("source_account_id"),
            destinationAccountId = rs.getLong("destination_account_id"),
            notes = rs.getString("notes"),
        )
    }

    private val baseSelect = """
        SELECT tr.id, tr.portfolio_id, tr.listing_id, l.asset_id, tr.quantity, tr.asset_fee_quantity,
               tr.date, tr.source_account_id, tr.destination_account_id, tr.notes
        FROM transfers tr
        JOIN listings l ON l.id = tr.listing_id
    """.trimIndent()

    fun findAllForPortfolio(portfolioId: Long): List<Transfer> =
        jdbcTemplate.query("$baseSelect WHERE tr.portfolio_id = ? ORDER BY tr.date ASC, tr.id ASC", rowMapper, portfolioId)

    fun findById(id: Long): Transfer? = try {
        jdbcTemplate.queryForObject("$baseSelect WHERE tr.id = ?", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    fun create(transfer: Transfer): Transfer {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO transfers (portfolio_id, listing_id, quantity, asset_fee_quantity, date, source_account_id, destination_account_id, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, transfer.portfolioId)
                setLong(2, transfer.listingId)
                setBigDecimal(3, transfer.quantity)
                setBigDecimal(4, transfer.assetFeeQuantity)
                setObject(5, transfer.date)
                setLong(6, transfer.sourceAccountId)
                setLong(7, transfer.destinationAccountId)
                setString(8, transfer.notes)
            }
        }, keyHolder)
        return transfer.copy(id = keyHolder.key!!.toLong())
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM transfers WHERE id = ?", id)
    }

    fun existsForAccountInPortfolio(accountId: Long, portfolioId: Long): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transfers WHERE portfolio_id = ? AND (source_account_id = ? OR destination_account_id = ?)",
            Int::class.java, portfolioId, accountId, accountId,
        )!! > 0
}
