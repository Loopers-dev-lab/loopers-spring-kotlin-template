package com.loopers.domain.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OrderRepository {
    fun findById(id: Long): Order?
    fun findByUserId(userId: Long, pageable: Pageable): Page<Order>
    fun save(order: Order): Order
}
