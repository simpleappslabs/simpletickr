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
    private val recordTransactionUseCase: RecordTransactionUseCase,
    private val amendTransactionUseCase: AmendTransactionUseCase,
    private val removeTransactionUseCase: RemoveTransactionUseCase,
) : TransactionsApi {

    override fun listTransactions(portfolioId: Long?): ResponseEntity<List<TransactionModel>> =
        ResponseEntity.ok(transactionRepository.findAll(portfolioId).map { it.toModel() })

    override fun getTransaction(id: Long): ResponseEntity<TransactionModel> {
        val transaction = transactionRepository.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction.toModel())
    }

    override fun recordTransaction(portfolioId: Long, transactionRequest: TransactionRequest): ResponseEntity<TransactionModel> {
        val command = RecordTransactionCommand(
            listingId = transactionRequest.listingId,
            type = TransactionType.valueOf(transactionRequest.type.value),
            quantity = BigDecimal.valueOf(transactionRequest.quantity),
            price = BigDecimal.valueOf(transactionRequest.price),
            date = transactionRequest.date,
            fees = transactionRequest.fees?.let { BigDecimal.valueOf(it) },
        )
        val transaction = recordTransactionUseCase.execute(portfolioId, command)
        return ResponseEntity.status(201).body(transaction.toModel())
    }

    override fun amendTransaction(portfolioId: Long, id: Long, transactionRequest: TransactionRequest): ResponseEntity<TransactionModel> {
        val command = AmendTransactionCommand(
            listingId = transactionRequest.listingId,
            type = TransactionType.valueOf(transactionRequest.type.value),
            quantity = BigDecimal.valueOf(transactionRequest.quantity),
            price = BigDecimal.valueOf(transactionRequest.price),
            date = transactionRequest.date,
            fees = transactionRequest.fees?.let { BigDecimal.valueOf(it) },
        )
        val transaction = amendTransactionUseCase.execute(portfolioId, id, command)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction.toModel())
    }

    override fun removeTransaction(portfolioId: Long, id: Long): ResponseEntity<Unit> {
        if (!removeTransactionUseCase.execute(portfolioId, id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    private fun Transaction.toModel() = TransactionModel(
        id = id,
        portfolioId = portfolioId,
        listingId = listingId,
        assetId = assetId,
        type = GeneratedTransactionType.valueOf(type.name),
        quantity = quantity.toDouble(),
        price = price.toDouble(),
        date = date,
        fees = fees?.toDouble(),
    )
}
