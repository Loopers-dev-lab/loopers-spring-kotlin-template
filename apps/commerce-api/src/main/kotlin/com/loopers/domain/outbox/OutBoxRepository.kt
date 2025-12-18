package com.loopers.domain.outbox

interface OutBoxRepository {
    fun save(outbox: OutBoxModel): OutBoxModel

    fun findByEventId(eventId: String): OutBoxModel?
}
