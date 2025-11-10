package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findByUserId(userId: Long): List<Order>
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Order>
}
