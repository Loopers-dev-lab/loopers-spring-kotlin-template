package com.loopers.application.order

data class CreateOrderRequest(
    val memberId: String,
    val items: List<OrderItemRequest>,
)

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int,
)
