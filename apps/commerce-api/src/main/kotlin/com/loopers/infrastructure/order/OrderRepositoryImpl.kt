package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderRepository
import org.springframework.stereotype.Component

@Component
class OrderRepositoryImpl(private val orderJpaRepository: OrderJpaRepository) : OrderRepository {

    override fun save(order: OrderModel): OrderModel = orderJpaRepository.saveAndFlush(order)
    override fun findByOrderId(orderId: Long): OrderModel? = orderJpaRepository.findById(orderId).orElse(null)
    override fun findByOrderKey(orderKey: String): OrderModel? = orderJpaRepository.findByOrderKey(orderKey)
}
