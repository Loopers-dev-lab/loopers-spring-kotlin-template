package com.loopers.domain.outbox

import com.github.f4b6a3.uuid.UuidCreator
import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * Transactional Outbox Pattern 구현을 위한 엔티티
 *
 * 도메인 트랜잭션과 메시지 발행의 원자성을 보장하기 위해 사용
 * - 비즈니스 트랜잭션과 동일한 트랜잭션에서 이벤트를 저장
 * - 별도 폴러가 주기적으로 PENDING 상태의 이벤트를 조회하여 Kafka로 발행
 */
@Entity
@Table(
    name = "outbox",
    indexes = [
        Index(name = "idx_outbox_status_created_at", columnList = "status, created_at"),
        Index(name = "idx_outbox_aggregate_id_type", columnList = "aggregate_id, aggregate_type"),
        Index(name = "idx_outbox_event_id", columnList = "event_id"),
    ],
)
class Outbox(

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    val eventId: String,

    @Column(name = "aggregate_type", nullable = false, length = 100)
    val aggregateType: AggregateType,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "processed_at")
    var processedAt: ZonedDateTime? = null,

    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
) : BaseEntity() {

    fun markProcessing() {
        this.status = OutboxStatus.PROCESSING
        this.processedAt = ZonedDateTime.now()
    }

    fun markCompleted() {
        this.status = OutboxStatus.PUBLISHED
        this.publishedAt = ZonedDateTime.now()
    }

    fun markFailed(errorMessage: String) {
        this.errorMessage = errorMessage
        this.status = OutboxStatus.FAILED
    }

    companion object {
        fun create(
            aggregateType: AggregateType,
            aggregateId: String,
            eventType: String,
            payload: String,
        ): Outbox {
            return Outbox(
                eventId = UuidCreator.getTimeOrderedEpoch().toString(),
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = payload,
            )
        }
    }
}

enum class OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
}

enum class AggregateType {
    PRODUCT,
    ORDER,
    USER,
}
