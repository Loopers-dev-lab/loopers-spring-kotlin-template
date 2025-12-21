package com.loopers.domain.outbox

interface OutBoxRepository {
    fun save(outbox: OutBoxModel): OutBoxModel

    fun findByEventId(eventId: String): OutBoxModel?

    fun findAllByStatus(status: OutboxStatus): List<OutBoxModel>

    fun findAllByStatusIn(statuses: List<OutboxStatus>): List<OutBoxModel>
}
