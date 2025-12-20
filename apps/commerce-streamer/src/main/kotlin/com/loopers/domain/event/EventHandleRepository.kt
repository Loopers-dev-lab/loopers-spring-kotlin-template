package com.loopers.domain.event

interface EventHandleRepository {

    fun findByEventId(
            eventId: String,
    ): EventHandleModel?

    fun save(eventHandle: EventHandleModel): EventHandleModel
}
