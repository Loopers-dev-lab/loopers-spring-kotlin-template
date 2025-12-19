package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class OutboxRdbRepository(
    private val outboxJpaRepository: OutboxJpaRepository,
) : OutboxRepository {

    override fun save(outbox: Outbox): Outbox {
        return outboxJpaRepository.saveAndFlush(outbox)
    }

    override fun findAllByIdGreaterThanOrderByIdAsc(cursorId: Long, limit: Int): List<Outbox> {
        return outboxJpaRepository.findAllByIdGreaterThanOrderByIdAsc(
            cursorId = cursorId,
            pageable = PageRequest.of(0, limit),
        )
    }
}
