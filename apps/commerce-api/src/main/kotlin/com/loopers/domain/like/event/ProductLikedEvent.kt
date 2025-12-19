package com.loopers.domain.like.event

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class ProductLikedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PRODUCT_LIKED",
    override val aggregateId: Long, // productId (partitionKey)
    override val occurredAt: Instant = Instant.now(),

    val likeId: Long,
    val memberId: String,
    val productId: Long,
    val likedAt: Instant = Instant.now(),
) : DomainEvent
