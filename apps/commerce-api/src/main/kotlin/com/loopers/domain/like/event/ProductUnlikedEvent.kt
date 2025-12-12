package com.loopers.domain.like.event

import java.time.Instant

data class ProductUnlikedEvent(
    val productId: Long,
    val memberId: Long,
    val unlikedAt: Instant
)
