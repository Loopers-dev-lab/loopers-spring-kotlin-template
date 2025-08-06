package com.loopers.domain.order.dto.command

import com.loopers.domain.order.dto.command.OrderItemCommand.Register.Item
import com.loopers.domain.order.entity.Order
import com.loopers.domain.order.entity.Order.Status
import java.math.BigDecimal

class OrderCommand {
    data class RequestOrder(
        val userId: Long,
        val originalPrice: BigDecimal,
        val finalPrice: BigDecimal,
        val status: Status = Status.ORDER_REQUEST,
        val items: List<Item>,
    ) {
        fun toEntity(): Order {
            return Order.create(userId, originalPrice, finalPrice, status)
        }

        fun toItemCommand(orderId: Long): OrderItemCommand.Register {
            return OrderItemCommand.Register(orderId, items)
        }
    }
}
