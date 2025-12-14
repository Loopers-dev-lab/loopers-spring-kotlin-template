package com.loopers.domain.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.ZonedDateTime

/**
 * 애그리게이트별 마지막 처리 이벤트 타임스탬프 관리
 *
 * 이벤트 순서 보장을 위해 애그리게이트별로 마지막 처리된 이벤트의 시간을 기록
 * - consumer_group: 컨슈머 그룹
 * - aggregate_id: 애그리게이트 식별자 (예: product-{productId})
 * - last_processed_at: 마지막으로 처리한 이벤트 시간
 */
@Entity
@Table(name = "event_processing_timestamp")
@IdClass(EventProcessingTimestampId::class)
class EventProcessingTimestamp(
    @Id
    @Column(name = "consumer_group", nullable = false, length = 100)
    val consumerGroup: String,

    @Id
    @Column(name = "aggregate_id", nullable = false, length = 200)
    val aggregateId: String,

    @Column(name = "last_processed_at", nullable = false)
    var lastProcessedAt: ZonedDateTime,
) {
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

        /**
         * 상품 애그리게이트 ID 생성
         */
        fun generateProductAggregateId(productId: Long): String {
            return "product-$productId"
        }

        /**
         * 주문 애그리게이트 ID 생성
         */
        fun generateOrderAggregateId(orderId: Long): String {
            return "order-$orderId"
        }
    }
}

/**
 * 복합키 클래스
 */
data class EventProcessingTimestampId(
    val consumerGroup: String = "",
    val aggregateId: String = "",
) : Serializable
