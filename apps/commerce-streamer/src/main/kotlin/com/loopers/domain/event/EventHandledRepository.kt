package com.loopers.domain.event

interface EventHandledRepository {
    fun existsById(eventId: String): Boolean
    fun existsByEventIdAndAggregateId(eventId: String, aggregateId: String): Boolean
    fun save(eventHandled: EventHandled): EventHandled
}
