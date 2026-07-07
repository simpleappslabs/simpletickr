package com.simpletickr.transaction

import com.simpletickr.fx.model.FxRateSource
import com.simpletickr.generated.api.TransactionsApi
import com.simpletickr.generated.model.CryptoTradeRequest
import com.simpletickr.generated.model.CryptoTradeResponse
import com.simpletickr.generated.model.TransactionPage
import com.simpletickr.generated.model.TransactionRequest
import com.simpletickr.trade.RecordCryptoTradeUseCase
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionFilter
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transaction.usecase.AmendTransactionUseCase
import com.simpletickr.transaction.usecase.DeleteTransactionUseCase
import com.simpletickr.transaction.usecase.RecordTransactionUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import com.simpletickr.generated.model.FxRateSource as GeneratedFxRateSource
import com.simpletickr.generated.model.Transaction as TransactionModel
import com.simpletickr.generated.model.TransactionType as GeneratedTransactionType

@RestController
class TransactionController(
    private val transactionRepository: TransactionRepository,
    private val recordTransactionUseCase: RecordTransactionUseCase,
    private val amendTransactionUseCase: AmendTransactionUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val recordCryptoTradeUseCase: RecordCryptoTradeUseCase,
) : TransactionsApi {

    override fun listTransactions(
        portfolioId: Long?,
        type: GeneratedTransactionType?,
        listingId: Long?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        page: Int,
        size: Int,
    ): ResponseEntity<TransactionPage> {
        if (page < 0 || size <= 0 || size > 200) return ResponseEntity.badRequest().build()
        if (dateFrom != null && dateTo != null && dateFrom > dateTo) return ResponseEntity.badRequest().build()

        val filter = TransactionFilter(
            portfolioId = portfolioId,
            type = type?.let { TransactionType.valueOf(it.value) },
            listingId = listingId,
            dateFrom = dateFrom,
            dateTo = dateTo,
        )
        val items = transactionRepository.findAll(filter, page, size)
        val total = transactionRepository.count(filter)
        val totalPages = ((total + size - 1) / size).toInt()
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
            broker = transactionRequest.broker,
            notes = transactionRequest.notes,
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
            broker = transactionRequest.broker,
            notes = transactionRequest.notes,
        )
        val transaction = amendTransactionUseCase.execute(portfolioId, id, command)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction.toModel())
    }

    override fun removeTransaction(portfolioId: Long, id: Long): ResponseEntity<Unit> {
        if (!deleteTransactionUseCase.execute(portfolioId, id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    override fun recordCryptoTrade(portfolioId: Long, cryptoTradeRequest: CryptoTradeRequest): ResponseEntity<CryptoTradeResponse> {
        val command = RecordCryptoTradeCommand(
            sellListingId = cryptoTradeRequest.sellListingId,
            sellQuantity = BigDecimal.valueOf(cryptoTradeRequest.sellQuantity),
            sellPrice = BigDecimal.valueOf(cryptoTradeRequest.sellPrice),
            buyListingId = cryptoTradeRequest.buyListingId,
            buyQuantity = BigDecimal.valueOf(cryptoTradeRequest.buyQuantity),
            buyPrice = BigDecimal.valueOf(cryptoTradeRequest.buyPrice),
            date = cryptoTradeRequest.date,
            fees = cryptoTradeRequest.fees?.let { BigDecimal.valueOf(it) },
            broker = cryptoTradeRequest.broker,
            notes = cryptoTradeRequest.notes,
        )
        val trade = recordCryptoTradeUseCase.execute(portfolioId, command)
        return ResponseEntity.status(201).body(CryptoTradeResponse(
            id = trade.id,
            sell = trade.sell.toModel(),
            buy = trade.buy.toModel(),
        ))
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
        broker = broker,
        notes = notes,
        tradeId = tradeId,
    )
}
