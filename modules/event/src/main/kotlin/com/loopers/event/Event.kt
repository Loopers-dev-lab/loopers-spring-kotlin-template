package com.loopers.event

data class Event<T : EventPayload>(
    val eventId: Long,
    val eventType: EventType,
    val payload: T,
    val timestamp: Long = System.currentTimeMillis(),
)
