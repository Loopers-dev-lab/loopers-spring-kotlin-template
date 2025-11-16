package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderDetail
import org.springframework.data.jpa.repository.JpaRepository

interface OrderDetailJpaRepository : JpaRepository<OrderDetail, Long> {
    fun findAllByOrderId(orderId: Long): List<OrderDetail>
}
