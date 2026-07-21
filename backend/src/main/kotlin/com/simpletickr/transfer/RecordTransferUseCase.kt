package com.simpletickr.transfer

import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.portfolio.HoldingService
import com.simpletickr.transaction.persistence.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class RecordTransferUseCase(
    private val transferRepository: TransferRepository,
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
    private val accountRepository: AccountRepository,
    private val holdingService: HoldingService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(portfolioId: Long, command: RecordTransferCommand): Transfer {
        log.info(
            "Recording transfer: portfolioId={}, listing={}, quantity={}",
            portfolioId, command.listingId, command.quantity,
        )

        require(command.sourceAccountId != command.destinationAccountId) {
            "Source and destination accounts must be different"
        }

        val listing = listingRepository.findById(command.listingId)
            ?: throw IllegalArgumentException("Listing ${command.listingId} not found")

        accountRepository.findById(command.sourceAccountId)
            ?: throw IllegalArgumentException("Account ${command.sourceAccountId} not found")
        accountRepository.findById(command.destinationAccountId)
            ?: throw IllegalArgumentException("Account ${command.destinationAccountId} not found")

        // Holdings aren't tracked per account, so this can't verify the source account
        // specifically holds enough — it's a best-effort guard against picking an account
        // that's never actually been used in this portfolio. The destination account is
        // deliberately not checked: using a brand-new account for the first time is normal.
        val sourceHasActivity = transactionRepository.existsForAccountInPortfolio(command.sourceAccountId, portfolioId) ||
            transferRepository.existsForAccountInPortfolio(command.sourceAccountId, portfolioId)
        require(sourceHasActivity) {
            "Source account ${command.sourceAccountId} has no prior activity in portfolio $portfolioId"
        }

        // "Do we hold enough" is evaluated as of the transfer's own date, not "right now" — so a
        // backdated transfer can't be validated against knowledge that didn't exist yet then.
        val heldAsOfDate = holdingService.getHoldings(portfolioId, asOf = command.date)
            .find { it.listingId == command.listingId }?.quantity ?: BigDecimal.ZERO
        require(command.quantity <= heldAsOfDate) {
            "Cannot transfer ${command.quantity}: only $heldAsOfDate held in portfolio $portfolioId as of ${command.date}"
        }

        return transferRepository.create(Transfer(
            id = 0L,
            portfolioId = portfolioId,
            listingId = command.listingId,
            assetId = listing.assetId,
            quantity = command.quantity,
            assetFeeQuantity = command.assetFeeQuantity,
            date = command.date,
            sourceAccountId = command.sourceAccountId,
            destinationAccountId = command.destinationAccountId,
            notes = command.notes,
        ))
    }
}
