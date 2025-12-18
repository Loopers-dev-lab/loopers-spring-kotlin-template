package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutBoxModel
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OutBoxJpaRepository : JpaRepository<OutBoxModel, Long> {
    fun findAllByStatus(status: OutboxStatus): List<OutBoxModel>

    fun findByEventId(eventId: String): OutBoxModel?
}
