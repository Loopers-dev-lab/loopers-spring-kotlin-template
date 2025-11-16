package com.loopers.domain.order

import org.springframework.stereotype.Service

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    fun createOrder(userId: Long, orderItems: List<OrderItem>): Order {
        val order = Order(userId = userId, items = orderItems)
        return orderRepository.save(order)
    }
}
