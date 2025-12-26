package com.loopers.domain.product.event

data class StockDepletedEvent(
    val productId: Long,
)
