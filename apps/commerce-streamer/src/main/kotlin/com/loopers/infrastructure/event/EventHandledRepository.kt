package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandled
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledRepository : JpaRepository<EventHandled, String> {
    fun existsByEventId(eventId: String): Boolean
}
