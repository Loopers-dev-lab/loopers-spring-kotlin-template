package com.loopers.domain.order.event

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class OrderCreatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "ORDER_CREATED",
    override val aggregateId: Long, // orderId (partitionKey)
    override val occurredAt: Instant = Instant.now(),

    val orderId: Long,
    val memberId: String,
    val orderAmount: Long,
    val couponId: Long?,
    val orderItems: List<OrderItemDto>, // 상품별 수량 정보 (판매량 집계용)
    val createdAt: Instant = Instant.now(),
) : DomainEvent

data class OrderItemDto(
    val productId: Long,
    val quantity: Int,
    val price: Long
)
