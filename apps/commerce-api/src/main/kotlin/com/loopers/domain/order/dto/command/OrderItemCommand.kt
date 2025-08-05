package com.loopers.domain.order.dto.command

import com.loopers.domain.order.entity.OrderItem

class OrderItemCommand {
    data class Register(
        val orderId: Long,
        val items: List<Item>,
    ) {
        data class Item(
            val productOptionId: Long,
            val quantity: Int,
        )

        fun toEntities(): List<OrderItem> {
            return items.map { OrderItem.create(orderId, it.productOptionId, it.quantity) }
        }
    }
}
