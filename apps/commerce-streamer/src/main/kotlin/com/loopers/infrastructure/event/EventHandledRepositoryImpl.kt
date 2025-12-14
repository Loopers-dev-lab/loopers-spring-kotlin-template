package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class EventHandledRepositoryImpl(
    private val jpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {

    override fun save(eventHandled: EventHandled): EventHandled {
        return jpaRepository.save(eventHandled)
    }

    override fun existsByEventKey(
        eventType: String,
        aggregateType: String,
        aggregateId: Long,
        eventVersion: Long,
    ): Boolean {
        return jpaRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndEventVersion(
            eventType = eventType,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventVersion = eventVersion,
        )
    }

    override fun deleteHandledEventsBefore(handledBefore: ZonedDateTime): Int {
        return jpaRepository.deleteByHandledAtBefore(handledBefore)
    }
}
