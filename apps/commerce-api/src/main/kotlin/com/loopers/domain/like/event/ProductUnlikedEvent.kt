package com.loopers.domain.like.event

data class ProductUnlikedEvent(
    val productId: Long,
    val memberId: Long,
    val unlikedAt: String
)
