package com.simpletickr.account.persistence

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class AccountRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Account> { rs, _ ->
        Account(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            name = rs.getString("name"),
            broker = rs.getString("broker"),
            accountType = AccountType.valueOf(rs.getString("account_type")),
            currency = rs.getString("currency"),
            accountNumber = rs.getString("account_number"),
            institution = rs.getString("institution"),
            transactionCount = rs.getLong("transaction_count"),
        )
    }

    private val baseSelect = """
        SELECT a.id, a.user_id, a.name, a.broker, a.account_type, a.currency, a.account_number, a.institution,
               COUNT(t.id) AS transaction_count
        FROM accounts a
        LEFT JOIN transactions t ON t.account_id = a.id
    """.trimIndent()

    fun findAllForUser(userId: Long): List<Account> =
        jdbcTemplate.query("$baseSelect WHERE a.user_id = ? GROUP BY a.id ORDER BY a.name ASC", rowMapper, userId)

    fun findById(id: Long): Account? = try {
        jdbcTemplate.queryForObject("$baseSelect WHERE a.id = ? GROUP BY a.id", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    // Deliberately not findById(accountId)?.userId == userId — that query joins and aggregates
    // every transaction on the account just to answer a boolean, which gets expensive as an
    // account accumulates history. This is a single indexed lookup instead.
    fun isOwnedBy(accountId: Long, userId: Long): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM accounts WHERE id = ? AND user_id = ?)",
            Boolean::class.java, accountId, userId,
        )!!

    fun save(account: Account): Account {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO accounts (user_id, name, broker, account_type, currency, account_number, institution) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, account.userId)
                setString(2, account.name)
                setString(3, account.broker)
                setString(4, account.accountType.name)
                setString(5, account.currency)
                setString(6, account.accountNumber)
                setString(7, account.institution)
            }
        }, keyHolder)
        return account.copy(id = keyHolder.key!!.toLong())
    }

    fun update(account: Account): Account? {
        val updated = jdbcTemplate.update(
            "UPDATE accounts SET name = ?, broker = ?, account_type = ?, currency = ?, account_number = ?, institution = ? WHERE id = ?",
            account.name, account.broker, account.accountType.name, account.currency,
            account.accountNumber, account.institution, account.id,
        )
        return if (updated == 0) null else account
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id)
    }
}
