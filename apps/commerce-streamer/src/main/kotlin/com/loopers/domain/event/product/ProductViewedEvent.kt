package com.loopers.domain.product.event

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class ProductViewedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PRODUCT_VIEWED",
    override val aggregateId: Long, // productId (partitionKey)
    override val occurredAt: Instant = Instant.now(),

    val productId: Long,
    val memberId: String?,  // 비로그인 사용자는 null
    val viewedAt: Instant = Instant.now(),
) : DomainEvent
