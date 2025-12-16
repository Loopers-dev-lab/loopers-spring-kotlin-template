package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

/**
 * 이벤트 멱등 처리를 위한 테이블
 *
 * Kafka Consumer가 동일한 이벤트를 중복으로 처리하지 않도록 보장
 * - event_id: 이벤트 고유 식별자 (UUID)
 * - aggregate_id: 집합체 식별자 (productId, orderId 등)
 * - event_type: 이벤트 타입
 * - event_timestamp: 이벤트 발생 시각 (순서 보장용)
 * - (event_id, aggregate_id) 조합으로 멱등성 보장
 */
@Entity
@Table(
    name = "event_handled",
    indexes = [
        Index(name = "idx_event_type_handled_at", columnList = "event_type, handled_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_event_id_aggregate_id", columnNames = ["event_id", "aggregate_id"]),
    ],
)
class EventHandled(
    @Column(name = "event_id", nullable = false, length = 100)
    val eventId: String,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "event_timestamp", nullable = false)
    val eventTimestamp: ZonedDateTime = ZonedDateTime.now(),

) : BaseEntity() {
    companion object {
        fun create(
            eventId: String,
            aggregateId: String,
            eventType: String,
            eventTimestamp: ZonedDateTime,
        ): EventHandled {
            return EventHandled(
                eventId = eventId,
                aggregateId = aggregateId,
                eventType = eventType,
                eventTimestamp = eventTimestamp,
            )
        }
    }
}
