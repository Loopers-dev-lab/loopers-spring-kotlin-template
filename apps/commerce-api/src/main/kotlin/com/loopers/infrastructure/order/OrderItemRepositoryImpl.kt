package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItemRepository
import org.springframework.stereotype.Component

@Component
class OrderItemRepositoryImpl(
    private val orderItemJpaRepository: OrderItemJpaRepository,
) : OrderItemRepository {
    override fun save(orderItem: OrderItem): OrderItem {
        return orderItemJpaRepository.save(orderItem)
    }

    override fun findByOrderId(orderId: Long): List<OrderItem> {
        return orderItemJpaRepository.findByOrderId(orderId)
    }
}
