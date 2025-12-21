package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderModel, Long> {
    fun findByOrderKey(orderKey: String): OrderModel?

    fun findAllByStatus(status: OrderStatus): List<OrderModel>
}
