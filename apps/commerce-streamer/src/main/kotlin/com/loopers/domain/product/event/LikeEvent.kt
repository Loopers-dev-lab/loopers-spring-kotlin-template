package com.loopers.domain.product.event

data class LikeEvent(
    val productId: Long,
    val userId: Long,
)
