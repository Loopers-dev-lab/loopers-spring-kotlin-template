package com.loopers.domain.like.event

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class ProductUnlikedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PRODUCT_UNLIKED",
    override val aggregateId: Long,  // productId (partitionKey)
    override val occurredAt: Instant = Instant.now(),

    val productId: Long,
    val memberId: String,
    val unlikedAt: Instant = Instant.now(),
) : DomainEvent
