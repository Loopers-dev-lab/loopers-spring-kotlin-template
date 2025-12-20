package com.loopers.domain.event

import java.time.ZonedDateTime
import java.util.UUID

/**
 * Event Handled Repository
 */
interface EventHandledRepository {
    fun save(eventHandled: EventHandled): EventHandled
    fun existsByEventId(eventId: UUID): Boolean
    fun deleteHandledEventsBefore(handledBefore: ZonedDateTime): Int
}
