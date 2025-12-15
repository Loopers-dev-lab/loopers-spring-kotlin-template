package com.loopers.domain.audit

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table

/**
 * 감사 로그 엔티티
 *
 * 모든 비즈니스 이벤트를 빠짐없이 기록
 * - 원본 데이터 보존
 * - 이벤트 추적성 보장
 */
@Entity
@Table(name = "audit_logs")
class AuditLog(

    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    val eventId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "topic_name", nullable = false, length = 100)
    val topicName: String,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,

    @Lob
    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    val rawPayload: String,
) : BaseEntity() {
    companion object {
        fun create(
            eventId: String,
            eventType: String,
            topicName: String,
            aggregateId: String,
            rawPayload: String,
        ): AuditLog {
            return AuditLog(
                eventId = eventId,
                eventType = eventType,
                topicName = topicName,
                aggregateId = aggregateId,
                rawPayload = rawPayload,
            )
        }
    }
}
