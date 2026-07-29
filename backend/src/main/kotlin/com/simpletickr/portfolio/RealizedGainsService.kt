package com.simpletickr.portfolio

import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.gains.RealizationMethod
import com.simpletickr.gains.RealizedGainsCalculator
import com.simpletickr.gains.RealizedGainsReport
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transfer.TransferRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class RealizedGainsService(
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val assetRepository: AssetRepository,
) {

    fun getRealizedGains(portfolioId: Long, method: RealizationMethod, from: LocalDate, to: LocalDate): RealizedGainsReport {
        val transactions = transactionRepository.findAllForPortfolio(portfolioId)
        val transferFees = transferRepository.findAllForPortfolio(portfolioId)
            .mapNotNull { t ->
                t.assetFeeQuantity?.takeIf { it > BigDecimal.ZERO }
                    ?.let { RealizedGainsCalculator.TransferFeeEvent(t.id, t.assetId, t.date, it) }
            }
        val listingMap = assetRepository.findAll().flatMap { it.listings }.associateBy { it.id }
        return RealizedGainsCalculator.compute(transactions, transferFees, listingMap, method, from, to)
    }
}
