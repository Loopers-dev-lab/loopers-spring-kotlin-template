package com.loopers.support.event

import java.time.Instant

interface DomainEvent {
    val eventId: String
    val eventType: String
    val aggregateId: String
    val aggregateType: String
    val occurredAt: Instant
    val version: Int
}
