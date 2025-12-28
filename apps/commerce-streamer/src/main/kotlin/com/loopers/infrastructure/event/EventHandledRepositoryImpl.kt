package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class EventHandledRepositoryImpl(private val jpaRepository: EventHandledJpaRepository) : EventHandledRepository {

    override fun save(eventHandled: EventHandled): EventHandled = jpaRepository.save(eventHandled)

    override fun existsByEventId(eventId: UUID): Boolean = jpaRepository.existsByEventId(eventId.toString())

    override fun deleteHandledEventsBefore(handledBefore: LocalDateTime): Int =
        jpaRepository.deleteByHandledAtBefore(handledBefore)
}
