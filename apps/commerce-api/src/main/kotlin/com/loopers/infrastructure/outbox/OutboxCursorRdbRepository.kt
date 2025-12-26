package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
import org.springframework.stereotype.Repository

@Repository
class OutboxCursorRdbRepository(
    private val outboxCursorJpaRepository: OutboxCursorJpaRepository,
) : OutboxCursorRepository {

    override fun save(cursor: OutboxCursor): OutboxCursor {
        return outboxCursorJpaRepository.saveAndFlush(cursor)
    }

    override fun findLatest(): OutboxCursor? {
        return outboxCursorJpaRepository.findTopByOrderByCreatedAtDesc()
    }
}
