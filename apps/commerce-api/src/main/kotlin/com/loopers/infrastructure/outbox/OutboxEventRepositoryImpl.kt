package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxEventStatus
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class OutboxEventRepositoryImpl(
    private val jpaRepository: OutboxEventJpaRepository,
) : OutboxEventRepository {

    override fun save(outboxEvent: OutboxEvent): OutboxEvent {
        return jpaRepository.save(outboxEvent)
    }

    override fun findById(id: Long): OutboxEvent? {
        return jpaRepository.findById(id).orElse(null)
    }

    override fun findPendingEvents(limit: Int): List<OutboxEvent> {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, limit)
    }

    override fun findFailedEventsOlderThan(createdBefore: ZonedDateTime): List<OutboxEvent> {
        return jpaRepository.findByStatusAndCreatedAtBefore(OutboxEventStatus.FAILED, createdBefore)
    }

    override fun deletePublishedEventsBefore(publishedBefore: ZonedDateTime): Int {
        return jpaRepository.deleteByStatusAndPublishedAtBefore(OutboxEventStatus.PUBLISHED, publishedBefore)
    }
}
