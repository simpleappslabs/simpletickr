package com.simpletickr.user.dashboard

data class DashboardWidget(
    val id: Long,
    val type: DashboardWidgetType,
    val config: WidgetConfig,
    val label: String,
    val currency: String?,
)
