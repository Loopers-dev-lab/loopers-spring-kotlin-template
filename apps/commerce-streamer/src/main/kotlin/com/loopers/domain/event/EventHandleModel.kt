package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "event_handles")
class EventHandleModel(
    @Column
    val eventId: String,

    @Column
    val topic: String,
) : BaseEntity() {

    companion object {
        fun create(eventId: String, topic: String): EventHandleModel = EventHandleModel(eventId, topic)
    }
}
