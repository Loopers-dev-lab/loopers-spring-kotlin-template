package com.loopers.domain.order

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByUserId(userId: Long): List<Order>
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Order>
}
