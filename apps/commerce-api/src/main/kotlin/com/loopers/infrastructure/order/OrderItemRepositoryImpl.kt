package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderItemRepository
import com.loopers.domain.order.entity.OrderItem
import org.springframework.stereotype.Component

@Component
class OrderItemRepositoryImpl(
    private val orderItemJpaRepository: OrderItemJpaRepository,
) : OrderItemRepository {
    override fun findAll(orderId: Long): List<OrderItem> {
        return orderItemJpaRepository.findAllByOrderId(orderId)
    }

    override fun saveAll(orderItems: List<OrderItem>): List<OrderItem> {
        return orderItemJpaRepository.saveAll(orderItems)
    }
}
