package com.loopers.domain.order

interface OrderRepository {
    fun save(order: Order): Order
}
