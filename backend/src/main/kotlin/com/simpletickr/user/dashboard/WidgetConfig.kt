package com.simpletickr.user.dashboard

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

sealed interface WidgetConfig

@JsonIgnoreProperties(ignoreUnknown = true)
data class ListingPriceConfig(
    val targetId: Long,
    val range: String,
) : WidgetConfig

@JsonIgnoreProperties(ignoreUnknown = true)
data class PortfolioValueConfig(
    val targetId: Long,
    val range: String,
) : WidgetConfig
