package com.loopers.domain.order

import com.loopers.domain.order.entity.OrderItem

interface OrderItemRepository {
    fun findAll(orderId: Long): List<OrderItem>

    fun saveAll(orderItems: List<OrderItem>): List<OrderItem>
}
