package com.loopers.domain.like.event

data class ProductLikedEvent(
    val likeId: Long,
    val memberId: Long,
    val productId: Long,
    val likedAt: String
)
