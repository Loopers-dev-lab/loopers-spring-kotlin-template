package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * 애그리게이트별 마지막 처리 이벤트 타임스탬프 관리
 *
 * 이벤트 순서 보장을 위해 애그리게이트별로 마지막 처리된 이벤트의 시간을 기록
 * - consumer_group: 컨슈머 그룹
 * - aggregate_id: 애그리게이트 식별자 (예: productId)
 * - last_processed_at: 마지막으로 처리한 이벤트 시간
 */
@Entity
@Table(name = "event_processing_timestamp")
class EventProcessingTimestamp(
    @Column(name = "consumer_group", nullable = false, length = 100)
    val consumerGroup: String,

    @Column(name = "aggregate_id", nullable = false, length = 200)
    val aggregateId: String,

    @Column(name = "last_processed_at", nullable = false)
    var lastProcessedAt: ZonedDateTime,
) : BaseEntity() {

    companion object {
        fun create(
            consumerGroup: String,
            aggregateId: String,
            lastProcessedAt: ZonedDateTime,
        ): EventProcessingTimestamp {
            return EventProcessingTimestamp(
                consumerGroup = consumerGroup,
                aggregateId = aggregateId,
                lastProcessedAt = lastProcessedAt,
            )
        }
    }

    /**
     * 이벤트가 처리되어야 하는지 확인
     * 이벤트 시간이 마지막 처리 시간보다 이후여야 처리
     */
    fun shouldProcess(eventTimestamp: ZonedDateTime): Boolean {
        return eventTimestamp.isAfter(this.lastProcessedAt)
    }

    /**
     * 마지막 처리 시간 업데이트
     */
    fun updateLastProcessedAt(eventTimestamp: ZonedDateTime) {
        this.lastProcessedAt = eventTimestamp
    }
}
