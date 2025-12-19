package com.loopers.infrastructure.idempotency

import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class EventHandledRdbRepository(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {

    @Transactional
    override fun save(eventHandled: EventHandled): EventHandled {
        return eventHandledJpaRepository.saveAndFlush(eventHandled)
    }

    @Transactional(readOnly = true)
    override fun existsByAggregateTypeAndAggregateIdAndAction(
        aggregateType: String,
        aggregateId: String,
        action: String,
    ): Boolean {
        return eventHandledJpaRepository.existsByAggregateTypeAndAggregateIdAndAction(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            action = action,
        )
    }
}
