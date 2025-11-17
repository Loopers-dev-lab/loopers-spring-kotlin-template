package com.loopers.domain.order

import com.loopers.application.order.OrderCommand
import org.springframework.stereotype.Component

@Component
class OrderService(private val orderRepository: OrderRepository) {

    fun order(userId: Long, orderCommand: OrderCommand) = orderRepository.save(OrderModel.order(userId, orderCommand))
}
