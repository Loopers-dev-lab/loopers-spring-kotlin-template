package com.loopers.domain.like

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class LikeCreatedEventV1(
    val userId: Long,
    val productId: Long,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
