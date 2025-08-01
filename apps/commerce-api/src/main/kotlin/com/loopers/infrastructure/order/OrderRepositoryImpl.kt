package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.entity.Order
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun find(id: Long): Order? {
        return orderJpaRepository.findByIdOrNull(id)
    }

    override fun save(order: Order): Order {
        return orderJpaRepository.save(order)
    }
}
