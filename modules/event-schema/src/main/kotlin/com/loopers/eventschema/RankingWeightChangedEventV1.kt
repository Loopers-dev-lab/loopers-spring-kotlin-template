package com.loopers.eventschema

import java.time.Instant

data class RankingWeightChangedEventV1(
    val occurredAt: Instant,
)
