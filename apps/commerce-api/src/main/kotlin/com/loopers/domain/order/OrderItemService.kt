package com.loopers.domain.order

import com.loopers.domain.order.dto.command.OrderItemCommand
import com.loopers.domain.order.entity.OrderItem
import org.springframework.stereotype.Component

@Component
class OrderItemService(
    private val orderItemRepository: OrderItemRepository,
) {
    fun findAll(orderId: Long): List<OrderItem> {
        return orderItemRepository.findAll(orderId)
    }

    fun register(command: OrderItemCommand.Register): List<OrderItem> {
        return orderItemRepository.saveAll(command.toEntities())
    }
}
