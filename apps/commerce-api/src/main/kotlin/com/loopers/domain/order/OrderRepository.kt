package com.loopers.domain.order

interface OrderRepository {
    fun findById(id: Long): Order?
    fun save(order: Order): Order
}
