package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.stereotype.Repository

@Repository
class OrderRdbRepository(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun findById(id: Long): Order? {
        return orderJpaRepository.findByIdWithOrderItems(id)
    }

    override fun save(order: Order): Order {
        return orderJpaRepository.save(order)
    }
}
