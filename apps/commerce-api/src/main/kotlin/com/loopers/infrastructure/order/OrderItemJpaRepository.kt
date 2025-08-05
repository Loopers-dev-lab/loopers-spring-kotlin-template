package com.loopers.infrastructure.order

import com.loopers.domain.order.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemJpaRepository : JpaRepository<OrderItem, Long> {
    fun findAllByOrderId(orderId: Long): MutableList<OrderItem>
}
