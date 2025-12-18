package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import com.loopers.event.EventType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "event_handles")
class EventHandleModel(
    @Column
    val eventId: String,

    @Column
    @Enumerated(EnumType.STRING)
    val eventType: EventType,
) : BaseEntity()
