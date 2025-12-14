package com.loopers.domain.event

import java.time.ZonedDateTime

/**
 * Event Handled Repository
 */
interface EventHandledRepository {
    fun save(eventHandled: EventHandled): EventHandled
    fun existsByEventKey(
        eventType: String,
        aggregateType: String,
        aggregateId: Long,
        eventVersion: Long,
    ): Boolean
    fun deleteHandledEventsBefore(handledBefore: ZonedDateTime): Int
}
