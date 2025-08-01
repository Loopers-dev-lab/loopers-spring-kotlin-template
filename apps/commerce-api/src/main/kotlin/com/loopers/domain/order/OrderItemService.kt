package com.loopers.domain.order

import com.loopers.domain.order.dto.command.OrderItemCommand
import com.loopers.domain.order.entity.OrderItem
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class OrderItemService(
    private val orderItemRepository: OrderItemRepository,
) {
    fun findAll(orderId: Long): List<OrderItem> {
        return orderItemRepository.findAll(orderId)
    }

    fun register(command: OrderItemCommand.Register): List<OrderItem> {
        val duplicateOptionIds = command.items
            .groupingBy { it.productOptionId }
            .eachCount()
            .filter { it.value > 1 }

        if (duplicateOptionIds.isNotEmpty()) {
            throw CoreException(ErrorType.CONFLICT, "동일한 옵션이 중복되었습니다: ${duplicateOptionIds.keys}")
        }

        return orderItemRepository.saveAll(command.toEntities())
    }
}
