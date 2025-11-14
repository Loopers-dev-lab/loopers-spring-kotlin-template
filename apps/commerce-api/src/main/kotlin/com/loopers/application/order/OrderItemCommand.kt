package com.loopers.application.order

data class OrderItemCommand(
    val productId: Long,
    val quantity: Int,
)
