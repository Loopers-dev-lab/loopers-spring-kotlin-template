package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.OutboxCursor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OutboxCursorJpaRepository : JpaRepository<OutboxCursor, Long> {

    fun findTopByOrderByCreatedAtDesc(): OutboxCursor?
}
