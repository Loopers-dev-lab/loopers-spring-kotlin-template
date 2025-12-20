package com.loopers.support.outbox

import com.loopers.eventschema.CloudEventEnvelope
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

/**
 * Outbox 엔티티 - Transactional Outbox Pattern을 위한 메시지 저장소
 *
 * - BaseEntity를 상속하지 않음 (인프라 데이터)
 * - topic은 저장하지 않음 (eventType에서 도출)
 */
@Entity
@Table(name = "outbox")
class Outbox(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "event_id", nullable = false, length = 36)
    val eventId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "source", nullable = false, length = 100)
    val source: String,

    @Column(name = "aggregate_type", nullable = false, length = 100)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 255)
    val aggregateId: String,

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    val payload: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null,
) {
    fun markForRetry(interval: Duration, now: Instant = Instant.now()) {
        nextRetryAt = now.plus(interval)
    }

    fun isExpired(maxAge: Duration, now: Instant = Instant.now()): Boolean {
        return Duration.between(createdAt, now) > maxAge
    }

    companion object {
        fun from(envelope: CloudEventEnvelope): Outbox {
            return Outbox(
                eventId = envelope.id,
                eventType = envelope.type,
                source = envelope.source,
                aggregateType = envelope.aggregateType,
                aggregateId = envelope.aggregateId,
                payload = envelope.payload,
                createdAt = envelope.time,
                nextRetryAt = null,
            )
        }
    }
}
