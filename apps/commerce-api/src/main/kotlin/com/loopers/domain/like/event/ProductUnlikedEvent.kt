package com.loopers.domain.like.event

import java.time.Instant

data class ProductUnlikedEvent(
    val productId: Long,
    val memberId: String,
    val unlikedAt: Instant
)
