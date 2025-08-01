package com.loopers.domain.order.dto.result

import com.loopers.domain.order.entity.OrderItem
import java.time.ZonedDateTime

class OrderItemResult {
    data class OrderItemDetail(
        val id: Long,
        val orderId: Long,
        val productOptionId: Long,
        val quantity: Int,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {

        companion object {
            fun from(orderItem: OrderItem): OrderItemDetail {
                return OrderItemDetail(
                    orderItem.id,
                    orderItem.orderId,
                    orderItem.productOptionId,
                    orderItem.quantity.value,
                    orderItem.createdAt,
                    orderItem.updatedAt,
                )
            }
        }
    }

    data class OrderItemDetails(
        val orders: List<OrderItemDetail>,
    ) {
        companion object {
            fun from(orderItems: List<OrderItem>): OrderItemDetails {
                return OrderItemDetails(
                    orderItems.map { OrderItemDetail.from(it) },
                )
            }
        }
    }
}
