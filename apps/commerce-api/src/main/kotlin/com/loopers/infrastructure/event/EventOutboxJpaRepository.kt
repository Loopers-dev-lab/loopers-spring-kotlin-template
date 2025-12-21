package com.loopers.infrastructure.event

import com.loopers.domain.event.EventOutbox
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface EventOutboxJpaRepository : JpaRepository<EventOutbox, Long> {
    fun existsByEventId(eventId: String): Boolean
    fun findTop100ByProcessedFalseOrderByCreatedAtAsc(): List<EventOutbox>

    // 클린업용 삭제 메서드
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM EventOutbox e WHERE e.processed = true AND e.processedAt < :threshold")
    fun deleteByProcessedTrueAndProcessedAtBefore(threshold: Instant) : Int
}
