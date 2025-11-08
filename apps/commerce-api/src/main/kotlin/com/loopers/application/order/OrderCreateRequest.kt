package com.loopers.application.order

data class OrderCreateRequest(
    val items: List<OrderItemRequest>
)

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int
)
