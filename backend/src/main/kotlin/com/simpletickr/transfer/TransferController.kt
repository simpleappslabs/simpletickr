package com.simpletickr.transfer

import com.simpletickr.account.AccountService
import com.simpletickr.account.model.Account
import com.simpletickr.generated.api.TransfersApi
import com.simpletickr.generated.model.TransferRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import com.simpletickr.generated.model.Account as AccountModel
import com.simpletickr.generated.model.AccountType as GeneratedAccountType
import com.simpletickr.generated.model.Transfer as TransferModel

@RestController
class TransferController(
    private val recordTransferUseCase: RecordTransferUseCase,
    private val deleteTransferUseCase: DeleteTransferUseCase,
    private val transferRepository: TransferRepository,
    private val accountService: AccountService,
) : TransfersApi {

    override fun listTransfersForPortfolio(portfolioId: Long): ResponseEntity<List<TransferModel>> {
        val transfers = transferRepository.findAllForPortfolio(portfolioId)
        val accountsById = accountService.listAccounts().associateBy { it.id }
        return ResponseEntity.ok(transfers.map { it.toModel(accountsById) })
    }

    override fun recordTransfer(portfolioId: Long, transferRequest: TransferRequest): ResponseEntity<TransferModel> {
        val command = RecordTransferCommand(
            listingId = transferRequest.listingId,
            quantity = BigDecimal.valueOf(transferRequest.quantity),
            assetFeeQuantity = transferRequest.assetFeeQuantity?.let { BigDecimal.valueOf(it) },
            date = transferRequest.date,
            sourceAccountId = transferRequest.sourceAccountId,
            destinationAccountId = transferRequest.destinationAccountId,
            notes = transferRequest.notes,
        )
        val transfer = recordTransferUseCase.execute(portfolioId, command)
        val sourceAccount = accountService.getAccount(transfer.sourceAccountId)!!
        val destinationAccount = accountService.getAccount(transfer.destinationAccountId)!!
        val accountsById = mapOf(sourceAccount.id to sourceAccount, destinationAccount.id to destinationAccount)
        return ResponseEntity.status(201).body(transfer.toModel(accountsById))
    }

    override fun removeTransfer(portfolioId: Long, id: Long): ResponseEntity<Unit> {
        if (!deleteTransferUseCase.execute(portfolioId, id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    private fun Transfer.toModel(accountsById: Map<Long, Account>) = TransferModel(
        id = id,
        portfolioId = portfolioId,
        listingId = listingId,
        quantity = quantity.toDouble(),
        assetFeeQuantity = assetFeeQuantity?.toDouble(),
        date = date,
        sourceAccountId = sourceAccountId,
        sourceAccount = accountsById[sourceAccountId]!!.toModel(),
        destinationAccountId = destinationAccountId,
        destinationAccount = accountsById[destinationAccountId]!!.toModel(),
        notes = notes,
    )

    private fun Account.toModel() = AccountModel(
        id = id,
        name = name,
        broker = broker,
        accountType = GeneratedAccountType.valueOf(accountType.name),
        currency = currency,
        accountNumber = accountNumber,
        institution = institution,
        transactionCount = transactionCount,
    )
}
