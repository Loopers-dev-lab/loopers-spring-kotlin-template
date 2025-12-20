package com.loopers.domain.ranking.dto

import java.time.ZonedDateTime

data class LikeScoreEvent(
    val productId: Long,
    val dateKey: String,
    val eventId: String,
    val eventType: String,
    val eventTimestamp: ZonedDateTime,
)
