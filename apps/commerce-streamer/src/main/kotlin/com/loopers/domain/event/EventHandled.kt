package com.loopers.domain.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * 이벤트 멱등 처리를 위한 테이블
 *
 * Kafka Consumer가 동일한 이벤트를 중복으로 처리하지 않도록 보장
 * - event_id: 이벤트 고유 식별자 (topic + partition + offset 조합)
 * - event_type: 이벤트 타입
 * - event_timestamp: 이벤트 발생 시각 (순서 보장용)
 * - handled_at: 처리 시각
 */
@Entity
@Table(
    name = "event_handled",
    indexes = [
        Index(name = "idx_event_type_handled_at", columnList = "event_type, handled_at"),
    ],
)
class EventHandled(
    @Id
    @Column(name = "event_id", length = 500)
    val eventId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "event_timestamp", nullable = false)
    val eventTimestamp: ZonedDateTime = ZonedDateTime.now(),

    ) {
    companion object {
        fun create(
            eventId: String,
            eventType: String,
            eventTimestamp: ZonedDateTime,
        ): EventHandled {
            return EventHandled(
                eventId = eventId,
                eventType = eventType,
                eventTimestamp = eventTimestamp,
            )
        }

        /**
         * Kafka 메시지로부터 고유 이벤트 ID 생성
         * topic-partition-offset 조합으로 유일성 보장
         */
        fun generateEventId(
            topic: String,
            partition: Int,
            offset: Long,
        ): String {
            return "$topic-$partition-$offset"
        }
    }
}
