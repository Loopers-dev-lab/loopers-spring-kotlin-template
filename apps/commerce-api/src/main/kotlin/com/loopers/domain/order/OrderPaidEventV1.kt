package com.loopers.domain.order

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class OrderPaidEventV1(
    val orderId: Long,
    val userId: Long,
    val totalAmount: Long,
    val orderItems: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {

    data class OrderItemSnapshot(
        val productId: Long,
        val quantity: Int,
    )

    companion object {
        fun from(order: Order): OrderPaidEventV1 {
            return OrderPaidEventV1(
                orderId = order.id,
                userId = order.userId,
                totalAmount = order.totalAmount.amount.toLong(),
                orderItems = order.orderItems.map { item ->
                    OrderItemSnapshot(
                        productId = item.productId,
                        quantity = item.quantity,
                    )
                },
            )
        }
    }
}
