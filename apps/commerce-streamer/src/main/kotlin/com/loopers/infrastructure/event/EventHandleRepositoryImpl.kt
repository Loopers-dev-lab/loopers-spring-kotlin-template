package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandleModel
import com.loopers.domain.event.EventHandleRepository
import org.springframework.stereotype.Component

@Component
class EventHandleRepositoryImpl(private val eventHandleJpaRepository: EventHandleJpaRepository) :
        EventHandleRepository {

        override fun findByEventId(
                eventId: String,
        ): EventHandleModel? = eventHandleJpaRepository.findByEventId(eventId)

        override fun save(eventHandle: EventHandleModel): EventHandleModel =
                eventHandleJpaRepository.saveAndFlush(eventHandle)
}
