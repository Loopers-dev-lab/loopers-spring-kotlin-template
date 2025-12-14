package com.loopers.domain.outbox

import org.springframework.data.domain.Pageable

interface OutboxRepository {
    fun findById(outboxId: Long): Outbox?
    fun findByStatusIn(statuses: List<OutboxStatus>, pageable: Pageable): List<Outbox>
    fun save(outbox: Outbox): Outbox
}
