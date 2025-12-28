package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

/**
 * 이벤트 처리 기록 테이블 (멱등성 보장)
 * Consumer가 같은 이벤트를 중복 처리하지 않도록 방지
 */
@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_handled_event_id",
            columnNames = ["event_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_event_handled_created", columnList = "created_at"),
        Index(name = "idx_event_handled_aggregate", columnList = "event_type, aggregate_type, aggregate_id"),
    ],
)
class EventHandled(
    /**
     * 이벤트 고유 ID (UUID)
     * 이벤트의 고유성을 보장하는 키
     */
    @Column(name = "event_id", nullable = false, length = 36)
    val eventId: String,

    /**
     * 이벤트 타입 (e.g. OrderCreated, LikeAdded)
     */
    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    /**
     * 집계 타입 (e.g. Order, Product, Like)
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    val aggregateType: String,

    /**
     * 집계 ID
     */
    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: Long,

    /**
     * 처리 완료 시각
     */
    @Column(name = "handled_at", nullable = false)
    val handledAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 처리자 (consumer group id 등)
     */
    @Column(name = "handled_by", nullable = false, length = 100)
    val handledBy: String,
) : BaseEntity() {

    companion object {
        /**
         * 새로운 처리 기록 생성
         */
        fun create(
            eventId: UUID,
            eventType: String,
            aggregateType: String,
            aggregateId: Long,
            handledBy: String = "commerce-streamer",
        ): EventHandled = EventHandled(
                eventId = eventId.toString(),
                eventType = eventType,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                handledBy = handledBy,
            )
    }
}
