package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findByMemberId(memberId: String, pageable: Pageable): Page<Order>
    fun findByStatusAndCreatedAtBefore(status: OrderStatus, time: ZonedDateTime): List<Order>
}
