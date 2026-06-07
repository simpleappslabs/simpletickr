package com.simpletickr.transaction

import com.simpletickr.generated.api.TransactionsApi
import com.simpletickr.generated.model.TransactionRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import com.simpletickr.generated.model.Transaction as TransactionModel
import com.simpletickr.generated.model.TransactionType as GeneratedTransactionType

@RestController
class TransactionController(
    private val transactionRepository: TransactionRepository,
) : TransactionsApi {

    override fun listTransactions(portfolioId: Long?): ResponseEntity<List<TransactionModel>> =
        ResponseEntity.ok(transactionRepository.findAll(portfolioId).map { it.toModel() })

    override fun getTransaction(id: Long): ResponseEntity<TransactionModel> {
        val transaction = transactionRepository.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction.toModel())
    }

    override fun createTransaction(transactionRequest: TransactionRequest): ResponseEntity<TransactionModel> {
        val transaction = transactionRepository.save(
            portfolioId = transactionRequest.portfolioId,
            assetId = transactionRequest.assetId,
            type = TransactionType.valueOf(transactionRequest.type.value),
            quantity = BigDecimal.valueOf(transactionRequest.quantity),
            price = BigDecimal.valueOf(transactionRequest.price),
            date = transactionRequest.date,
            fees = transactionRequest.fees?.let { BigDecimal.valueOf(it) },
        )
        return ResponseEntity.status(201).body(transaction.toModel())
    }

    override fun updateTransaction(id: Long, transactionRequest: TransactionRequest): ResponseEntity<TransactionModel> {
        val transaction = transactionRepository.update(
            id = id,
            assetId = transactionRequest.assetId,
            type = TransactionType.valueOf(transactionRequest.type.value),
            quantity = BigDecimal.valueOf(transactionRequest.quantity),
            price = BigDecimal.valueOf(transactionRequest.price),
            date = transactionRequest.date,
            fees = transactionRequest.fees?.let { BigDecimal.valueOf(it) },
        ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction.toModel())
    }

    override fun deleteTransaction(id: Long): ResponseEntity<Unit> {
        if (transactionRepository.findById(id) == null) return ResponseEntity.notFound().build()
        transactionRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    private fun Transaction.toModel() = TransactionModel(
        id = id,
        portfolioId = portfolioId,
        assetId = assetId,
        type = GeneratedTransactionType.valueOf(type.name),
        quantity = quantity.toDouble(),
        price = price.toDouble(),
        date = date,
        fees = fees?.toDouble(),
    )
}