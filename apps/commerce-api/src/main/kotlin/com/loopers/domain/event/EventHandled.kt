package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

/**
 * 이벤트 처리 기록 테이블 (멱등성 보장)
 * Consumer가 같은 이벤트를 중복 처리하지 않도록 방지
 */
@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_handled_key",
            columnNames = ["eventType", "aggregateType", "aggregateId", "eventVersion"],
        ),
    ],
    indexes = [
        Index(name = "idx_event_handled_created", columnList = "createdAt"),
    ],
)
class EventHandled(
    /**
     * 이벤트 타입 (e.g. OrderCreated, LikeAdded)
     */
    @Column(nullable = false, length = 100)
    val eventType: String,

    /**
     * 집계 타입 (e.g. Order, Product, Like)
     */
    @Column(nullable = false, length = 50)
    val aggregateType: String,

    /**
     * 집계 ID
     */
    @Column(nullable = false)
    val aggregateId: Long,

    /**
     * 이벤트 버전 (동일 집계에 대한 순서 보장)
     * 보통 updatedAt 또는 version 컬럼 값 사용
     */
    @Column(nullable = false)
    val eventVersion: Long,

    /**
     * 처리 완료 시각
     */
    @Column(nullable = false)
    val handledAt: ZonedDateTime = ZonedDateTime.now(),

    /**
     * 처리자 (consumer group id 등)
     */
    @Column(nullable = false, length = 100)
    val handledBy: String,
) : BaseEntity() {

    companion object {
        /**
         * 새로운 처리 기록 생성
         */
        fun create(
            eventType: String,
            aggregateType: String,
            aggregateId: Long,
            eventVersion: Long,
            handledBy: String = "commerce-consumer",
        ): EventHandled {
            return EventHandled(
                eventType = eventType,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventVersion = eventVersion,
                handledBy = handledBy,
            )
        }
    }
}
