package com.loopers.domain.order

interface OrderItemRepository {
    fun save(orderItem: OrderItem): OrderItem
    fun findByOrderId(orderId: Long): List<OrderItem>
}
