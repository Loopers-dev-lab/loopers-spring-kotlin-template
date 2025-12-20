package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandleModel
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandleJpaRepository : JpaRepository<EventHandleModel, Long> {

    fun findByEventId(
        eventId: String,
    ): EventHandleModel?
}
