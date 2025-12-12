package com.loopers.domain.like

import com.loopers.support.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class LikeCreatedEventV1(
    val userId: Long,
    val productId: Long,
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "LikeCreatedEventV1",
    override val aggregateId: String = productId.toString(),
    override val aggregateType: String = "ProductLike",
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1,
) : DomainEvent
