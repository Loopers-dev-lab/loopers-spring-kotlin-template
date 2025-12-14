package com.loopers.domain.order

import com.loopers.support.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class OrderCanceledEventV1(
    val orderId: Long,
    val orderItems: List<OrderItemSnapshot>,
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "OrderCanceledEvent",
    override val aggregateId: String = orderId.toString(),
    override val aggregateType: String = "Order",
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1,
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
