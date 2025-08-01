package com.loopers.domain.order

import com.loopers.domain.order.dto.command.OrderCommand
import com.loopers.domain.order.entity.Order
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class OrderService(
    private val orderRepository: OrderRepository,
) {
    fun get(id: Long): Order {
        return orderRepository.find(id)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 주문을 찾을 수 없습니다.")
    }

    fun request(command: OrderCommand.RequestOrder): Order {
        return orderRepository.save(command.toEntity())
    }
}
