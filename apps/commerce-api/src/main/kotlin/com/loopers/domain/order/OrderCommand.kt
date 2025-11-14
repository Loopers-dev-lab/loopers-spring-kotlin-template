package com.loopers.domain.order

data class CreateOrderCommand(
    val memberId: String,
    val items: List<OrderItemCommand>,
)

data class OrderItemCommand(
    val productId: Long,
    val quantity: Int,
)
