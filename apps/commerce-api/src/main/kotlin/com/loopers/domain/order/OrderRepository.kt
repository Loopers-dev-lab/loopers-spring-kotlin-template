package com.loopers.domain.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByIdOrThrow(id: Long): Order
    fun findByMemberId(memberId: String, pageable: Pageable): Page<Order>
}
