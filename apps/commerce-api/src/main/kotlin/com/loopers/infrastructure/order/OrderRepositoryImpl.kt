package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun findById(id: Long): Order? = orderJpaRepository.findByIdOrNull(id)

    override fun findByIdWithLock(id: Long): Order? = orderJpaRepository.findByIdWithLock(id)

    override fun findByUserId(
        userId: Long,
        pageable: Pageable,
    ): Page<Order> = orderJpaRepository.findByUserId(userId, pageable)

    override fun save(order: Order): Order = orderJpaRepository.save(order)
}
