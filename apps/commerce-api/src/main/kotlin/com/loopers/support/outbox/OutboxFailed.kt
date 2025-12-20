package com.loopers.support.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * OutboxFailed 엔티티 - 발행 실패 메시지 저장소
 *
 * - BaseEntity를 상속하지 않음 (인프라 데이터)
 * - 단순히 실패 기록만 저장 (재시도 로직은 별도 관리)
 */
@Entity
@Table(name = "outbox_failed")
class OutboxFailed(
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

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "failed_at", nullable = false)
    val failedAt: Instant,
) {

    companion object {
        fun from(outbox: Outbox, errorMessage: String): OutboxFailed {
            return OutboxFailed(
                eventId = outbox.eventId,
                eventType = outbox.eventType,
                source = outbox.source,
                aggregateType = outbox.aggregateType,
                aggregateId = outbox.aggregateId,
                payload = outbox.payload,
                errorMessage = errorMessage,
                failedAt = Instant.now(),
            )
        }
    }
}
