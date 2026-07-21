package com.simpletickr.transfer

import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.fx.FxRateService
import com.simpletickr.fx.model.FxRateSource
import com.simpletickr.portfolio.CostBasisService
import com.simpletickr.portfolio.HoldingService
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.RecordTransferCommand
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class RecordTransferUseCase(
    private val transferRepository: TransferRepository,
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
    private val assetRepository: AssetRepository,
    private val portfolioRepository: PortfolioRepository,
    private val holdingService: HoldingService,
    private val costBasisService: CostBasisService,
    private val fxRateService: FxRateService,
    private val userSettingsRepository: UserSettingsRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(sourcePortfolioId: Long, command: RecordTransferCommand): Transfer {
        log.info(
            "Recording transfer: sourcePortfolioId={}, destinationPortfolioId={}, listing={}, quantity={}",
            sourcePortfolioId, command.destinationPortfolioId, command.listingId, command.quantity,
        )

        require(command.sourceAccountId != command.destinationAccountId) {
            "Source and destination accounts must be different"
        }

        val listing = listingRepository.findById(command.listingId)
            ?: throw IllegalArgumentException("Listing ${command.listingId} not found")
        val asset = assetRepository.findById(listing.assetId)
            ?: throw IllegalArgumentException("Asset for listing ${command.listingId} not found")

        portfolioRepository.findById(command.destinationPortfolioId)
            ?: throw IllegalArgumentException("Portfolio ${command.destinationPortfolioId} not found")

        // "Do we hold enough" is a holdings-read-model question, answered by HoldingService directly.
        val holding = holdingService.getHoldings(sourcePortfolioId).find { it.listingId == command.listingId }
            ?: throw IllegalArgumentException(
                "No holding of listing ${command.listingId} in portfolio $sourcePortfolioId to transfer"
            )
        require(command.quantity <= holding.quantity) {
            "Cannot transfer ${command.quantity}: only ${holding.quantity} held in portfolio $sourcePortfolioId"
        }

        // Cost basis, not market value: a transfer has no market price. FX is resolved only so the
        // frozen basis figure can be expressed in each leg's local currency — it does not affect gains.
        val currentAverageCost = costBasisService.currentAverageCost(sourcePortfolioId, command.listingId)
            ?: throw IllegalArgumentException(
                "No cost basis available for listing ${command.listingId} in portfolio $sourcePortfolioId"
            )

        val receivedQuantity = command.quantity - (command.assetFeeQuantity ?: BigDecimal.ZERO)
        require(receivedQuantity > BigDecimal.ZERO) {
            "Received quantity must be positive after deducting the asset fee (fee too close to transferred quantity)"
        }

        val baseCurrency = userSettingsRepository.find().baseCurrency
        val (fxRate, fxRateSource) = resolveFxRate(baseCurrency, listing.currency.value, command.date)

        val transferId = transferRepository.create()

        val sourceLeg = transactionRepository.save(Transaction(
            id = 0L,
            portfolioId = sourcePortfolioId,
            listingId = command.listingId,
            assetId = listing.assetId,
            type = TransactionType.TRANSFER_OUT,
            quantity = command.quantity,
            price = currentAverageCost,
            date = command.date,
            fees = null,
            fxRate = fxRate,
            fxRateSource = fxRateSource,
            accountId = command.sourceAccountId,
            notes = command.notes,
            transferId = transferId,
            assetFeeQuantity = command.assetFeeQuantity,
        ))

        val destinationPrice = (command.quantity * currentAverageCost)
            .divide(receivedQuantity, 10, RoundingMode.HALF_UP)

        val destinationLeg = transactionRepository.save(Transaction(
            id = 0L,
            portfolioId = command.destinationPortfolioId,
            listingId = command.listingId,
            assetId = listing.assetId,
            type = TransactionType.TRANSFER_IN,
            quantity = receivedQuantity,
            price = destinationPrice,
            date = command.date,
            fees = null,
            fxRate = fxRate,
            fxRateSource = fxRateSource,
            accountId = command.destinationAccountId,
            notes = command.notes,
            transferId = transferId,
        ))

        return Transfer(id = transferId, sourceLeg = sourceLeg, destinationLeg = destinationLeg)
    }

    private fun resolveFxRate(
        baseCurrency: CurrencyCode,
        listingCurrency: String,
        date: LocalDate,
    ): Pair<BigDecimal?, FxRateSource?> {
        val listingCurrencyCode = CurrencyCode(listingCurrency)
        if (listingCurrencyCode == baseCurrency) return null to null
        val found = fxRateService.lookupOrFetch(baseCurrency, listingCurrencyCode, date)
            ?: throw IllegalArgumentException(
                "No FX rate available for $baseCurrency/$listingCurrency on $date. Please provide the rate manually."
            )
        return found.rate to FxRateSource.AUTO
    }
}
