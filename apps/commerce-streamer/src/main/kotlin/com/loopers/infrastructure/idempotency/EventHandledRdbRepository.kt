package com.loopers.infrastructure.idempotency

import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import com.loopers.support.idempotency.IdempotencyResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Repository
class EventHandledRdbRepository(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    transactionManager: PlatformTransactionManager,
) : EventHandledRepository {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionTemplate.PROPAGATION_REQUIRES_NEW
    }

    override fun save(eventHandled: EventHandled): IdempotencyResult {
        return try {
            transactionTemplate.execute {
                eventHandledJpaRepository.saveAndFlush(eventHandled)
            }
            IdempotencyResult.Recorded
        } catch (e: Exception) {
            logger.warn("Failed to save idempotency key: {}", eventHandled.idempotencyKey, e)
            IdempotencyResult.RecordFailed
        }
    }

    override fun saveAll(eventHandledList: List<EventHandled>): IdempotencyResult {
        if (eventHandledList.isEmpty()) return IdempotencyResult.Recorded
        return try {
            transactionTemplate.execute {
                eventHandledJpaRepository.saveAllAndFlush(eventHandledList)
            }
            IdempotencyResult.Recorded
        } catch (e: Exception) {
            logger.warn("Failed to save {} idempotency keys", eventHandledList.size, e)
            IdempotencyResult.RecordFailed
        }
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
