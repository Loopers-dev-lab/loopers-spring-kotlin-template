package com.loopers.domain.outbox

import java.time.ZonedDateTime

/**
 * Outbox Event Repository
 */
interface OutboxEventRepository {
    fun save(outboxEvent: OutboxEvent): OutboxEvent
    fun findById(id: Long): OutboxEvent?
    fun findPendingEvents(limit: Int): List<OutboxEvent>
    fun findFailedEventsOlderThan(createdBefore: ZonedDateTime): List<OutboxEvent>
    fun deletePublishedEventsBefore(publishedBefore: ZonedDateTime): Int
}
