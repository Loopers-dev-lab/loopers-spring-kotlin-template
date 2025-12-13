package com.loopers.domain.order

import com.loopers.support.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class OrderCreatedEventV1(
    val orderId: Long,
    val orderItems: List<OrderItemSnapshot>,
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "OrderCreatedEvent",
    override val aggregateId: String = orderId.toString(),
    override val aggregateType: String = "Order",
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1,
) : DomainEvent
