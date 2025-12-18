package com.loopers.domain.order

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class OrderCreatedEventV1(
    val orderId: Long,
    val orderItems: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {

    data class OrderItemSnapshot(
        val productId: Long,
        val quantity: Int,
    )
}
