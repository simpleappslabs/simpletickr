package com.simpletickr.portfolio.model

import java.util.UUID

data class Portfolio(val id: Long, val uuid: UUID, val name: String, val userId: Long)
