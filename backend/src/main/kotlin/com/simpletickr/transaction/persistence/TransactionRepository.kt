package com.simpletickr.transaction.persistence

import com.simpletickr.asset.model.AssetType
import com.simpletickr.fx.model.FxRateSource
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

data class TransactionFilter(
    val portfolioId: Long? = null,
    val type: TransactionType? = null,
    val listingId: Long? = null,
    val assetType: AssetType? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val accountId: Long? = null,
)

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
            fxRate = rs.getBigDecimal("fx_rate"),
            fxRateSource = rs.getString("fx_rate_source")?.let { FxRateSource.valueOf(it) },
            externalId = rs.getString("external_id"),
            accountId = rs.getLong("account_id"),
            notes = rs.getString("notes"),
            tradeId = rs.getLong("trade_id").takeIf { !rs.wasNull() },
        )
    }

    private val baseSelect = """
        SELECT t.id, t.portfolio_id, t.listing_id, l.asset_id,
               t.type, t.quantity, t.price, t.date, t.fees, t.fx_rate, t.fx_rate_source, t.external_id,
               t.account_id, t.notes, t.trade_id
        FROM transactions t
        JOIN listings l ON l.id = t.listing_id
        JOIN assets a ON a.id = l.asset_id
    """.trimIndent()

    fun findAllForPortfolio(portfolioId: Long): List<Transaction> =
        jdbcTemplate.query("$baseSelect WHERE t.portfolio_id = ? ORDER BY t.date ASC, t.id ASC", rowMapper, portfolioId)

    private fun buildWhere(filter: TransactionFilter): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()
        filter.portfolioId?.let { conditions += "t.portfolio_id = ?"; params += it }
        filter.type?.let        { conditions += "t.type = ?";          params += it.name }
        filter.listingId?.let   { conditions += "t.listing_id = ?";   params += it }
        filter.assetType?.let   { conditions += "a.type = ?";          params += it.name }
        filter.dateFrom?.let    { conditions += "t.date >= ?";         params += it }
        filter.dateTo?.let      { conditions += "t.date <= ?";         params += it }
        filter.accountId?.let   { conditions += "t.account_id = ?";   params += it }
        val clause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        return clause to params
    }

    fun findAll(filter: TransactionFilter, page: Int = 0, size: Int = 25): List<Transaction> {
        val offset = page * size
        val (where, params) = buildWhere(filter)
        val allParams = (params + size + offset).toTypedArray<Any>()
        return jdbcTemplate.query(
            "$baseSelect $where ORDER BY t.date DESC, t.id DESC LIMIT ? OFFSET ?",
            rowMapper,
            *allParams,
        )
    }

    fun count(filter: TransactionFilter): Long {
        val (where, params) = buildWhere(filter)
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions t JOIN listings l ON l.id = t.listing_id JOIN assets a ON a.id = l.asset_id $where",
            Long::class.java,
            *params.toTypedArray(),
        )!!
    }

    fun findOldestTransactionDate(portfolioId: Long): LocalDate? =
        jdbcTemplate.query(
            "SELECT MIN(date) AS oldest FROM transactions WHERE portfolio_id = ?",
            { rs, _ -> rs.getDate("oldest")?.toLocalDate() },
            portfolioId
        ).firstOrNull()

    fun findDistinctListingIds(portfolioId: Long): List<Long> =
        jdbcTemplate.queryForList(
            "SELECT DISTINCT listing_id FROM transactions WHERE portfolio_id = ? ORDER BY listing_id",
            Long::class.java,
            portfolioId
        )

    fun findById(id: Long): Transaction? = try {
        jdbcTemplate.queryForObject("$baseSelect WHERE t.id = ?", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(transaction: Transaction): Transaction {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO transactions (portfolio_id, listing_id, type, quantity, price, date, fees, fx_rate, fx_rate_source, external_id, account_id, notes, trade_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, transaction.portfolioId)
                setLong(2, transaction.listingId)
                setString(3, transaction.type.name)
                setBigDecimal(4, transaction.quantity)
                setBigDecimal(5, transaction.price)
                setObject(6, transaction.date)
                setBigDecimal(7, transaction.fees)
                setBigDecimal(8, transaction.fxRate)
                setString(9, transaction.fxRateSource?.name)
                setString(10, transaction.externalId)
                setLong(11, transaction.accountId)
                setString(12, transaction.notes)
                if (transaction.tradeId != null) setLong(13, transaction.tradeId) else setNull(13, java.sql.Types.BIGINT)
            }
        }, keyHolder)
        return transaction.copy(id = keyHolder.key!!.toLong())
    }

    fun existsIdentical(
        portfolioId: Long, listingId: Long, date: LocalDate, type: TransactionType,
        quantity: BigDecimal, price: BigDecimal, fees: BigDecimal?, externalId: String?,
    ): Boolean = jdbcTemplate.queryForObject(
        """SELECT COUNT(*) FROM transactions
           WHERE portfolio_id = ? AND listing_id = ? AND date = ? AND type = ?
           AND quantity = ? AND price = ?
           AND fees IS NOT DISTINCT FROM ?
           AND external_id IS NOT DISTINCT FROM ?""",
        Int::class.java,
        portfolioId, listingId, date, type.name, quantity, price, fees, externalId
    )!! > 0

    fun existsByExternalId(portfolioId: Long, externalId: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE portfolio_id = ? AND external_id = ?",
            Int::class.java, portfolioId, externalId
        )!! > 0

    fun update(transaction: Transaction): Transaction? {
        val updated = jdbcTemplate.update(
            "UPDATE transactions SET listing_id = ?, type = ?, quantity = ?, price = ?, date = ?, fees = ?, fx_rate = ?, fx_rate_source = ?, account_id = ?, notes = ? WHERE id = ?",
            transaction.listingId, transaction.type.name, transaction.quantity, transaction.price,
            transaction.date, transaction.fees, transaction.fxRate, transaction.fxRateSource?.name,
            transaction.accountId, transaction.notes, transaction.id
        )
        return if (updated == 0) null else transaction
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM transactions WHERE id = ?", id)
    }
}
