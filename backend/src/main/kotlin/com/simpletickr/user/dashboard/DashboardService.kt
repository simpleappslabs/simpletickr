package com.simpletickr.user.dashboard

import com.fasterxml.jackson.databind.ObjectMapper
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.user.dashboard.DashboardWidgetType.LISTING_PRICE
import com.simpletickr.user.dashboard.DashboardWidgetType.PORTFOLIO_VALUE
import org.springframework.stereotype.Service

@Service
class DashboardService(
    private val repository: DashboardWidgetRepository,
    private val listingRepository: ListingRepository,
    private val portfolioRepository: PortfolioRepository,
    private val objectMapper: ObjectMapper,
) {

    fun listWidgets(userId: Long): List<DashboardWidget> {
        val raw = repository.findAllForUser(userId)

        val listingIds = raw.filter { it.type == LISTING_PRICE }
            .map { it.parseConfig<ListingPriceConfig>().targetId }.toSet()
        val portfolioIds = raw.filter { it.type == PORTFOLIO_VALUE }
            .map { it.parseConfig<PortfolioValueConfig>().targetId }.toSet()

        val listings = listingRepository.findByIds(listingIds).associateBy { it.id }
        val portfolios = portfolioRepository.findByIds(portfolioIds).associateBy { it.id }

        return raw.map { item ->
            when (item.type) {
                LISTING_PRICE -> {
                    val config = item.parseConfig<ListingPriceConfig>()
                    val listing = listings[config.targetId]
                    DashboardWidget(item.id, item.type, config, listing?.ticker ?: "Unknown", listing?.currency?.value)
                }
                PORTFOLIO_VALUE -> {
                    val config = item.parseConfig<PortfolioValueConfig>()
                    val portfolio = portfolios[config.targetId]
                    DashboardWidget(item.id, item.type, config, portfolio?.name ?: "Unknown", null)
                }
            }
        }
    }

    fun addWidget(type: DashboardWidgetType, config: WidgetConfig, userId: Long): DashboardWidget {
        if (type == PORTFOLIO_VALUE) {
            val targetId = (config as PortfolioValueConfig).targetId
            require(portfolioRepository.isOwnedBy(targetId, userId)) { "Portfolio $targetId not found" }
        }
        val raw = repository.insert(type, config, userId)
        return enrich(raw)
    }

    fun updateWidgetRange(id: Long, range: String, userId: Long): DashboardWidget? {
        val raw = repository.findRawById(id)?.takeIf { it.userId == userId } ?: return null
        val updatedConfig = when (raw.type) {
            LISTING_PRICE -> raw.parseConfig<ListingPriceConfig>().copy(range = range)
            PORTFOLIO_VALUE -> raw.parseConfig<PortfolioValueConfig>().copy(range = range)
        }
        repository.updateConfig(id, updatedConfig)
        return enrich(RawDashboardWidget(raw.id, raw.userId, raw.type, objectMapper.writeValueAsString(updatedConfig)))
    }

    fun removeWidget(id: Long, userId: Long): Boolean {
        if (repository.findRawById(id)?.userId != userId) return false
        return repository.delete(id)
    }

    private fun enrich(raw: RawDashboardWidget): DashboardWidget = when (raw.type) {
        LISTING_PRICE -> {
            val config = raw.parseConfig<ListingPriceConfig>()
            val listing = listingRepository.findById(config.targetId)
            DashboardWidget(raw.id, raw.type, config, listing?.ticker ?: "Unknown", listing?.currency?.value)
        }
        PORTFOLIO_VALUE -> {
            val config = raw.parseConfig<PortfolioValueConfig>()
            val portfolio = portfolioRepository.findById(config.targetId)
            DashboardWidget(raw.id, raw.type, config, portfolio?.name ?: "Unknown", null)
        }
    }

    // reified T lets Jackson see the concrete type at the call site despite type erasure
    private inline fun <reified T : WidgetConfig> RawDashboardWidget.parseConfig(): T =
        objectMapper.readValue(configJson, T::class.java)
}
