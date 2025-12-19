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
    override fun existsByIdempotencyKey(idempotencyKey: String): Boolean {
        return eventHandledJpaRepository.existsByIdempotencyKey(idempotencyKey)
    }
}
