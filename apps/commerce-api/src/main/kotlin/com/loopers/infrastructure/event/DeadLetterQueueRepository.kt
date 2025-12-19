package com.loopers.infrastructure.event

import com.loopers.domain.event.DeadLetterQueue
import org.springframework.data.jpa.repository.JpaRepository

interface DeadLetterQueueRepository : JpaRepository<DeadLetterQueue, Long> {
    fun findTop100ByProcessedFalseOrderByCreatedAtAsc(): List<DeadLetterQueue>
}
