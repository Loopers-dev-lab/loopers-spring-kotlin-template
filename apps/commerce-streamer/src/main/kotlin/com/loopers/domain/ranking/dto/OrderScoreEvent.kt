package com.loopers.domain.ranking.dto

import java.time.ZonedDateTime

data class OrderScoreEvent(
    val dateKey: String,
    val eventId: String,
    val eventType: String,
    val eventTimestamp: ZonedDateTime,
    val items: List<OrderScoreItem>,
)

data class OrderScoreItem(
    val productId: Long,
    val quantity: Int,
    val price: Long,
)
