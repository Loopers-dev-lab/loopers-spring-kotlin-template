package com.loopers.domain.order

import com.loopers.domain.order.entity.Order

interface OrderRepository {
    fun find(id: Long): Order?

    fun save(order: Order): Order
}
