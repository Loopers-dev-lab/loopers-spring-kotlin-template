package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "outboxs")
class OutBoxModel(
    @Column
    val eventId: String,

    @Column
    val topic: String,

    @Column
    val payload: String,

    @Column
    @Enumerated(EnumType.STRING)
    var status: OutboxStatus = OutboxStatus.PENDING,
) : BaseEntity() {

    @Column
    var publishedAt: ZonedDateTime? = null

    fun markAsPublished() {
        this.status = OutboxStatus.PUBLISHED
        this.publishedAt = ZonedDateTime.now()
    }

    fun markAsFailed() {
        this.status = OutboxStatus.FAILED
    }

    companion object {
        fun create(
            eventId: String,
            topic: String,
            payload: String,
        ): OutBoxModel = OutBoxModel(eventId, topic, payload, OutboxStatus.PENDING)
    }
}
