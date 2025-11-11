package com.loopers.domain.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    fun getOrders(userId: Long, pageable: Pageable): Page<Order> {
        return orderRepository.findAllBy(userId, pageable)
    }
}
