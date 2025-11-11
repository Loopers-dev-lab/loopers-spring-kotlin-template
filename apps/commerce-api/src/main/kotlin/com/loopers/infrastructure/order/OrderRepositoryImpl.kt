package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderDetailJpaRepository: OrderDetailJpaRepository,
) : OrderRepository {
    override fun findAllBy(userId: Long, pageable: Pageable): Page<Order> {
        return orderJpaRepository.findAllByUserId(userId, pageable)
    }

    override fun findById(id: Long): Order? {
        return orderJpaRepository.findByIdOrNull(id)
    }

    override fun findAllOrderDetailBy(orderId: Long): List<OrderDetail> {
        return orderDetailJpaRepository.findAllByOrderId(orderId)
    }
}
