package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxEventStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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

    @Transactional
    override fun findPendingEvents(limit: Int): List<OutboxEvent> {
        // PENDING 이벤트를 비관적 락으로 조회 (SKIP LOCKED)
        val events = jpaRepository.findByStatusOrderByCreatedAtAscWithLock(
            OutboxEventStatus.PENDING,
            PageRequest.of(0, limit),
        )

        // 조회된 이벤트를 즉시 PROCESSING 상태로 변경
        // 이렇게 하면 다른 스케줄러 인스턴스가 같은 이벤트를 가져가지 않음
        events.forEach { event ->
            event.markAsProcessing()
            jpaRepository.save(event)
        }

        return events
    }

    override fun findFailedEventsOlderThan(createdBefore: ZonedDateTime): List<OutboxEvent> {
        return jpaRepository.findByStatusAndCreatedAtBefore(OutboxEventStatus.FAILED, createdBefore)
    }

    override fun deletePublishedEventsBefore(publishedBefore: ZonedDateTime): Int {
        return jpaRepository.deleteByStatusAndPublishedAtBefore(OutboxEventStatus.PUBLISHED, publishedBefore)
    }
}
