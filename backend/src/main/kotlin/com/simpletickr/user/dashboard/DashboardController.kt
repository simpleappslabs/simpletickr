package com.simpletickr.user.dashboard

import com.simpletickr.generated.api.DashboardApi
import com.simpletickr.generated.model.AddDashboardWidgetRequest
import com.simpletickr.generated.model.UpdateDashboardWidgetRequest
import com.simpletickr.user.dashboard.DashboardWidgetType.LISTING_PRICE
import com.simpletickr.user.dashboard.DashboardWidgetType.PORTFOLIO_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.DashboardWidget as GeneratedDashboardWidget
import com.simpletickr.generated.model.DashboardWidgetConfig as GeneratedDashboardWidgetConfig
import com.simpletickr.generated.model.DashboardWidgetType as GeneratedDashboardWidgetType

@RestController
class DashboardController(private val service: DashboardService) : DashboardApi {

    override fun listDashboardWidgets(): ResponseEntity<List<GeneratedDashboardWidget>> =
        ResponseEntity.ok(service.listWidgets().map { it.toModel() })

    override fun addDashboardWidget(addDashboardWidgetRequest: AddDashboardWidgetRequest): ResponseEntity<GeneratedDashboardWidget> {
        val type = DashboardWidgetType.valueOf(addDashboardWidgetRequest.type.value)
        val config = when (type) {
            LISTING_PRICE -> ListingPriceConfig(addDashboardWidgetRequest.config.targetId, addDashboardWidgetRequest.config.range)
            PORTFOLIO_VALUE -> PortfolioValueConfig(addDashboardWidgetRequest.config.targetId, addDashboardWidgetRequest.config.range)
        }
        val widget = service.addWidget(type, config)
        return ResponseEntity.status(201).body(widget.toModel())
    }

    override fun updateDashboardWidget(id: Long, updateDashboardWidgetRequest: UpdateDashboardWidgetRequest): ResponseEntity<GeneratedDashboardWidget> {
        val widget = service.updateWidgetRange(id, updateDashboardWidgetRequest.config.range)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(widget.toModel())
    }

    override fun removeDashboardWidget(id: Long): ResponseEntity<Unit> {
        if (!service.removeWidget(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    private fun DashboardWidget.toModel() = GeneratedDashboardWidget(
        id = id,
        type = GeneratedDashboardWidgetType.valueOf(type.name),
        config = GeneratedDashboardWidgetConfig(
            targetId = when (config) {
                is ListingPriceConfig -> config.targetId
                is PortfolioValueConfig -> config.targetId
            },
            range = when (config) {
                is ListingPriceConfig -> config.range
                is PortfolioValueConfig -> config.range
            },
        ),
        label = label,
        currency = currency,
    )
}
