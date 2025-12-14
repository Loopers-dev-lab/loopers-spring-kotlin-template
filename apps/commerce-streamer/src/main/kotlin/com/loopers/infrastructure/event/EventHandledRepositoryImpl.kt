package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import org.springframework.stereotype.Repository

@Repository
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {

    override fun existsById(eventId: String): Boolean {
        return eventHandledJpaRepository.existsById(eventId)
    }

    override fun save(eventHandled: EventHandled): EventHandled {
        return eventHandledJpaRepository.save(eventHandled)
    }
}
