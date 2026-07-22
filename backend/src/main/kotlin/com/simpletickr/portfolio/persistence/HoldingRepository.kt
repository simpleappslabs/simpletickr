package com.simpletickr.portfolio.persistence

import com.simpletickr.portfolio.model.Holding
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.TransactionType
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
        val accountId: Long,
    )

    // Returns raw transaction rows with listing/asset context. No aggregation.
    // Ordered for deterministic service-side processing.
    fun findTransactionRows(portfolioId: Long, asOf: LocalDate? = null): List<TransactionRow> {
        val dateClause = if (asOf != null) "AND t.date <= ?" else ""
        val params = if (asOf != null) arrayOf<Any>(portfolioId, asOf) else arrayOf<Any>(portfolioId)
        return jdbcTemplate.query("""
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
                t.date,
                t.account_id
            FROM transactions t
            JOIN listings l ON l.id = t.listing_id
            JOIN assets a ON a.id = l.asset_id
            WHERE t.portfolio_id = ? $dateClause
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
                accountId = rs.getLong("account_id"),
            )
        }, *params)
    }

    data class TransferFeeRow(
        val listingId: Long,
        val assetId: Long,
        val date: LocalDate,
        val feeQuantity: BigDecimal,
    )

    // Raw SQL directly against `transfers` — deliberately not routed through transfer.TransferRepository,
    // to keep the portfolio -> transfer dependency one-directional (RecordTransferUseCase already
    // depends on HoldingService; the reverse would create a package cycle).
    fun findTransferFeeRows(portfolioId: Long, asOf: LocalDate? = null): List<TransferFeeRow> {
        val dateClause = if (asOf != null) "AND tr.date <= ?" else ""
        val params = if (asOf != null) arrayOf<Any>(portfolioId, asOf) else arrayOf<Any>(portfolioId)
        return jdbcTemplate.query("""
            SELECT tr.listing_id, l.asset_id, tr.date, tr.asset_fee_quantity
            FROM transfers tr
            JOIN listings l ON l.id = tr.listing_id
            WHERE tr.portfolio_id = ?
              AND tr.asset_fee_quantity IS NOT NULL
              AND tr.asset_fee_quantity > 0
              $dateClause
            ORDER BY l.id ASC, tr.date ASC, tr.id ASC
        """.trimIndent(), { rs, _ ->
            TransferFeeRow(
                listingId = rs.getLong("listing_id"),
                assetId = rs.getLong("asset_id"),
                date = rs.getDate("date").toLocalDate(),
                feeQuantity = rs.getBigDecimal("asset_fee_quantity"),
            )
        }, *params)
    }

    data class TransferRow(
        val listingId: Long,
        val assetId: Long,
        val currency: CurrencyCode,
        val date: LocalDate,
        val quantity: BigDecimal,
        val feeQuantity: BigDecimal?,
        val sourceAccountId: Long,
        val destinationAccountId: Long,
    )

    // Every transfer, not just fee-bearing ones — needed to move quantity between accounts for
    // per-account holdings. Portfolio-level holdings only care about the fee (see
    // findTransferFeeRows above); this one carries the full picture.
    fun findTransferRows(portfolioId: Long, asOf: LocalDate? = null): List<TransferRow> {
        val dateClause = if (asOf != null) "AND tr.date <= ?" else ""
        val params = if (asOf != null) arrayOf<Any>(portfolioId, asOf) else arrayOf<Any>(portfolioId)
        return jdbcTemplate.query("""
            SELECT tr.listing_id, l.asset_id, l.currency, tr.date, tr.quantity, tr.asset_fee_quantity,
                   tr.source_account_id, tr.destination_account_id
            FROM transfers tr
            JOIN listings l ON l.id = tr.listing_id
            WHERE tr.portfolio_id = ? $dateClause
            ORDER BY l.id ASC, tr.date ASC, tr.id ASC
        """.trimIndent(), { rs, _ ->
            TransferRow(
                listingId = rs.getLong("listing_id"),
                assetId = rs.getLong("asset_id"),
                currency = CurrencyCode(rs.getString("currency")),
                date = rs.getDate("date").toLocalDate(),
                quantity = rs.getBigDecimal("quantity"),
                feeQuantity = rs.getBigDecimal("asset_fee_quantity"),
                sourceAccountId = rs.getLong("source_account_id"),
                destinationAccountId = rs.getLong("destination_account_id"),
            )
        }, *params)
    }
}
