package com.loopers.domain.product.event

data class OrderPaidEvent(
    val orderId: Long,
    val orderItems: List<OrderItem>,
) {
    data class OrderItem(
        val productId: Long,
        val quantity: Int,
    )
}
