package com.loopers.domain.event

interface EventHandledRepository {
    fun existsByEventIdAndAggregateId(eventId: String, aggregateId: String): Boolean
    fun save(eventHandled: EventHandled): EventHandled
}
