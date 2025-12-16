package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import org.springframework.stereotype.Repository

@Repository
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {

    override fun existsById(eventId: String): Boolean {
        return eventHandledJpaRepository.existsEventHandledByEventId(eventId)
    }

    override fun existsByEventIdAndAggregateId(eventId: String, aggregateId: String): Boolean {
        return eventHandledJpaRepository.existsByEventIdAndAggregateId(eventId, aggregateId)
    }

    override fun save(eventHandled: EventHandled): EventHandled {
        return eventHandledJpaRepository.save(eventHandled)
    }
}
