package com.loopers.domain.product

sealed interface ProductEvent {
    val productId: Long
    val userId: Long
}

data class ProductViewedEvent(override val productId: Long, override val userId: Long) :
        ProductEvent
