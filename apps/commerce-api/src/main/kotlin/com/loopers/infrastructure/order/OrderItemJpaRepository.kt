package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemJpaRepository : JpaRepository<OrderItem, Long> {
    fun findByOrderId(orderId: Long): List<OrderItem>
}
