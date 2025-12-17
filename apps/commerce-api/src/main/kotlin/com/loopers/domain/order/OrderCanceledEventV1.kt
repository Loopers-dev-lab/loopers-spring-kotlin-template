package com.loopers.domain.order

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class OrderCanceledEventV1(
    val orderId: Long,
    val orderItems: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {

    companion object {
        fun from(order: Order): OrderCanceledEventV1 {
            return OrderCanceledEventV1(
                orderId = order.id,
                orderItems = order.orderItems.map {
                    OrderItemSnapshot(
                        productId = it.productId,
                        quantity = it.quantity,
                    )
                },
            )
        }
    }

    data class OrderItemSnapshot(
        val productId: Long,
        val quantity: Int,
    )
}
