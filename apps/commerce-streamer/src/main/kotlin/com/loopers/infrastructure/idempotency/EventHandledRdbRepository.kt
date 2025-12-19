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

    @Transactional
    override fun saveAll(eventHandledList: List<EventHandled>): List<EventHandled> {
        if (eventHandledList.isEmpty()) return emptyList()
        return eventHandledJpaRepository.saveAllAndFlush(eventHandledList)
    }

    @Transactional(readOnly = true)
    override fun existsByIdempotencyKey(idempotencyKey: String): Boolean {
        return eventHandledJpaRepository.existsByIdempotencyKey(idempotencyKey)
    }

    @Transactional(readOnly = true)
    override fun findAllExistingKeys(idempotencyKeys: Set<String>): Set<String> {
        if (idempotencyKeys.isEmpty()) return emptySet()
        return eventHandledJpaRepository.findIdempotencyKeysByKeyIn(idempotencyKeys)
    }
}
