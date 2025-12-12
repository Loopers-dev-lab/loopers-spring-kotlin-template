package com.loopers.domain.like.event

import java.time.Instant

data class ProductLikedEvent(
    val likeId: Long,
    val memberId: Long,
    val productId: Long,
    val likedAt: Instant
)
