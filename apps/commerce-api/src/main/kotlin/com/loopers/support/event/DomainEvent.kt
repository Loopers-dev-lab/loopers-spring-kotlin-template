package com.loopers.support.event

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
