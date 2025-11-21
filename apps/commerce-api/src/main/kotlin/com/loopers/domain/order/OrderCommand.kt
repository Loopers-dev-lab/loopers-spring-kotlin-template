package com.loopers.domain.order

data class CreateOrderCommand(
    val memberId: String,
    val items: List<OrderItemCommand>,
    val couponId: Long? = null,
)

data class OrderItemCommand(
    val productId: Long,
    val quantity: Int,
)
