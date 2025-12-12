package com.loopers.domain.order.event

data class OrderCreatedEvent(
    val orderId: Long,
    val memberId: String,
    val orderAmount: Long,
    val couponId: Long?,
    val createdAt: String
)
