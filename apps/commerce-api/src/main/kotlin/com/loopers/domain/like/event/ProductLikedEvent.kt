package com.loopers.domain.like.event

import java.time.Instant

data class ProductLikedEvent(
    val likeId: Long,
    val memberId: String,
    val productId: Long,
    val likedAt: Instant
)
