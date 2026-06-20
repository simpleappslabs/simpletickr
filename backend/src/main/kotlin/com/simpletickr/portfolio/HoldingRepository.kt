package com.simpletickr.portfolio

import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.TransactionType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class HoldingRepository(private val jdbcTemplate: JdbcTemplate) {

    data class TransactionRow(
        val transactionId: Long,
        val assetId: Long,
        val assetName: String,
        val listingId: Long,
        val exchange: String?,
        val ticker: String,
        val currency: CurrencyCode,
        val type: TransactionType,
        val quantity: BigDecimal,
        val price: BigDecimal,
        val fees: BigDecimal?,
        val fxRate: BigDecimal?,
        val date: LocalDate,
    )

    // Returns raw transaction rows with listing/asset context. No aggregation.
    // Ordered for deterministic service-side processing.
    fun findTransactionRows(portfolioId: Long): List<TransactionRow> =
        jdbcTemplate.query("""
            SELECT
                t.id          AS transaction_id,
                a.id          AS asset_id,
                a.name        AS asset_name,
                l.id          AS listing_id,
                l.exchange,
                l.ticker,
                l.currency,
                t.type,
                t.quantity,
                t.price,
                t.fees,
                t.fx_rate,
                t.date
            FROM transactions t
            JOIN listings l ON l.id = t.listing_id
            JOIN assets a ON a.id = l.asset_id
            WHERE t.portfolio_id = ?
            ORDER BY a.id ASC, l.id ASC, t.date ASC, t.id ASC
        """.trimIndent(), { rs, _ ->
            TransactionRow(
                transactionId = rs.getLong("transaction_id"),
                assetId = rs.getLong("asset_id"),
                assetName = rs.getString("asset_name"),
                listingId = rs.getLong("listing_id"),
                exchange = rs.getString("exchange"),
                ticker = rs.getString("ticker"),
                currency = CurrencyCode(rs.getString("currency")),
                type = TransactionType.valueOf(rs.getString("type")),
                quantity = rs.getBigDecimal("quantity"),
                price = rs.getBigDecimal("price"),
                fees = rs.getBigDecimal("fees"),
                fxRate = rs.getBigDecimal("fx_rate"),
                date = rs.getDate("date").toLocalDate(),
            )
        }, portfolioId)
}
