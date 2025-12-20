package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.Outbox
import com.loopers.domain.outbox.OutboxRepository
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class OutboxRepositoryImpl(
    private val outboxJpaRepository: OutboxJpaRepository,
) : OutboxRepository {
    override fun findById(outboxId: Long): Outbox? {
        return outboxJpaRepository.findByIdOrNull(outboxId)
    }

    override fun findByStatusIn(
        statuses: List<OutboxStatus>,
        pageable: Pageable,
    ): List<Outbox> {
        return outboxJpaRepository.findByStatusIn(statuses, pageable)
    }

    override fun save(outbox: Outbox): Outbox {
        return outboxJpaRepository.save(outbox)
    }
}
