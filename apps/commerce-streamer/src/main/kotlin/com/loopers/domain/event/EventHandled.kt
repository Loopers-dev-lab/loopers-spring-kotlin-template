package com.loopers.domain.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "event_handled")
class EventHandled(
    @Id
    val eventId: String,

    @Column(nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false)
    val handledAt: Instant = Instant.now()
)
