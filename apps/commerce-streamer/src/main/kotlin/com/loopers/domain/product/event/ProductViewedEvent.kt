package com.loopers.domain.product.event

data class ProductViewedEvent(
    val productId: Long,
    val userId: Long,
)
