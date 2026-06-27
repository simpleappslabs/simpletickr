package com.simpletickr.transaction

import com.simpletickr.fx.model.FxRateSource
import com.simpletickr.generated.api.TransactionsApi
import com.simpletickr.generated.model.TransactionPage
import com.simpletickr.generated.model.TransactionRequest
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transaction.usecase.AmendTransactionUseCase
import com.simpletickr.transaction.usecase.DeleteTransactionUseCase
import com.simpletickr.transaction.usecase.RecordTransactionUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import com.simpletickr.generated.model.FxRateSource as GeneratedFxRateSource
import com.simpletickr.generated.model.Transaction as TransactionModel
import com.simpletickr.generated.model.TransactionType as GeneratedTransactionType

@RestController
class TransactionController(
    private val transactionRepository: TransactionRepository,
    private val recordTransactionUseCase: RecordTransactionUseCase,
    private val amendTransactionUseCase: AmendTransactionUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
) : TransactionsApi {

    override fun listTransactions(portfolioId: Long?, page: Int, size: Int): ResponseEntity<TransactionPage> {
        val items = transactionRepository.findAll(portfolioId, page, size)
        val total = transactionRepository.count(portfolioId)
        val totalPages = if (size == 0) 0 else ((total + size - 1) / size).toInt()
        return ResponseEntity.ok(TransactionPage(
            items = items.map { it.toModel() },
            page = page,
            propertySize = size,
            totalElements = total,
            totalPages = totalPages,
        ))
    }

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
            fxRate = transactionRequest.fxRate?.let { BigDecimal.valueOf(it) },
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
            fxRate = transactionRequest.fxRate?.let { BigDecimal.valueOf(it) },
        )
        val transaction = amendTransactionUseCase.execute(portfolioId, id, command)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction.toModel())
    }

    override fun removeTransaction(portfolioId: Long, id: Long): ResponseEntity<Unit> {
        if (!deleteTransactionUseCase.execute(portfolioId, id)) return ResponseEntity.notFound().build()
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
        fxRate = fxRate?.toDouble(),
        fxRateSource = fxRateSource?.let { GeneratedFxRateSource.valueOf(it.name) },
    )
}
