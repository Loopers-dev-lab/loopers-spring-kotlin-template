package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order {
        return orderJpaRepository.save(order)
    }

    override fun findById(id: Long): Order? {
        return orderJpaRepository.findByIdOrNull(id)
    }

    override fun findByUserId(userId: Long): List<Order> {
        return orderJpaRepository.findByUserId(userId)
    }

    override fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Order> {
        return orderJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
}
