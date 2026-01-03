package com.loopers.domain.outbox

import java.time.LocalDateTime

/**
 * Outbox Event Repository
 */
interface OutboxEventRepository {
    fun save(outboxEvent: OutboxEvent): OutboxEvent
    fun findById(id: Long): OutboxEvent?
    fun findPendingEvents(limit: Int): List<OutboxEvent>
    fun findFailedEventsOlderThan(createdBefore: LocalDateTime): List<OutboxEvent>
    fun deletePublishedEventsBefore(publishedBefore: LocalDateTime): Int
}
