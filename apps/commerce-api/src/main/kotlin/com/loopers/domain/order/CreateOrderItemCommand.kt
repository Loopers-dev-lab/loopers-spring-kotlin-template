package com.loopers.domain.order

data class CreateOrderItemCommand(
    val productId: Long,
    val quantity: Int,
)
