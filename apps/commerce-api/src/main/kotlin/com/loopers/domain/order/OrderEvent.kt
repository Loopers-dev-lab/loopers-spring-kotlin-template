package com.loopers.domain.order

import com.loopers.event.OrderItemSnapshot
import java.util.UUID

sealed interface OrderEvent {
    val eventId: String
    val orderId: Long
    val orderItems: List<OrderItemSnapshot>
    val couponId: Long?
}

data class OrderSuccessEvent(
    override val eventId: String,
    override val orderId: Long,
    override val orderItems: List<OrderItemSnapshot>,
    override val couponId: Long?,
) : OrderEvent {
    companion object {
        fun from(order: OrderModel, couponId: Long?): OrderSuccessEvent {
            val orderItems = order.orderItems.map { orderItem ->
                OrderItemSnapshot(
                    productId = orderItem.refProductId,
                    quantity = orderItem.quantity,
                )
            }
            return OrderSuccessEvent(eventId = UUID.randomUUID().toString(), order.id, orderItems, couponId)
        }
    }
}
