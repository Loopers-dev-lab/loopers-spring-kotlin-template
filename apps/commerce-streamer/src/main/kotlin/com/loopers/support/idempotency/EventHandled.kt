package com.loopers.support.idempotency

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * EventHandled 엔티티 - Consumer 멱등성 보장을 위한 처리 완료 이벤트 기록
 *
 * - BaseEntity를 상속하지 않음 (인프라 데이터)
 * - INSERT only (UPDATE 없음)
 */
@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_handled",
            columnNames = ["aggregate_type", "aggregate_id", "action"],
        ),
    ],
)
class EventHandled(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "aggregate_type", nullable = false, length = 100)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 255)
    val aggregateId: String,

    @Column(name = "action", nullable = false, length = 100)
    val action: String,

    @Column(name = "handled_at", nullable = false, updatable = false)
    val handledAt: Instant = Instant.now(),
) {
    companion object {
        fun create(
            aggregateType: String,
            aggregateId: String,
            action: String,
        ): EventHandled = EventHandled(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            action = action,
        )
    }
}
