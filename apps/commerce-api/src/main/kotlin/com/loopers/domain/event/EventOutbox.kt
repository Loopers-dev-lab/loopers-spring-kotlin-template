package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * Transactional Outbox Pattern
 * - 비즈니스 로직과 같은 트랜잭션으로 이벤트 저장
 * - At Least Once 보장
 */
@Entity
@Table(
    name = "event_outbox",
    indexes = [
        Index(name = "idx_event_outbox_processed", columnList = "processed, created_at"),
        Index(name = "idx_event_outbox_event_id", columnList = "eventId", unique = true)
    ]
)
class EventOutbox(
    /**
     * 이벤트 고유 ID (멱등성 체크)
     */
    @Column(nullable = false, unique = true, length = 36)
    val eventId: String,

    /**
     * 이벤트 타입
     */
    @Column(nullable = false, length = 50)
    val eventType: String,

    /**
     * Aggregate 타입 (PRODUCT, ORDER, MEMBER 등)
     */
    @Column(nullable = false, length = 20)
    val aggregateType: String,

    /**
     * Aggregate ID (Kafka PartitionKey)
     */
    @Column(nullable = false)
    val aggregateId: Long,

    /**
     * 이벤트 페이로드 (JSON)
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    /**
     * 이벤트 발생 시각
     */
    @Column(nullable = false)
    val occurredAt: Instant = Instant.now(),

    /**
     * Kafka 발행 완료 여부
     */
    @Column(nullable = false)
    var processed: Boolean = false,

    /**
     * Kafka 발행 완료 시각
     */
    var processedAt: Instant? = null,

    /**
     * Kafka 파티션 (발행 후 기록)
     */
    var kafkaPartition: Int? = null,

    /**
     * Kafka 오프셋 (발행 후 기록)
     */
    var kafkaOffset: Long? = null,

    /**
     * 재시도 횟수
     */
    @Column(nullable = false)
    var retryCount: Int = 0,

    /**
     * 마지막 에러 메시지
     */
    @Column(columnDefinition = "TEXT")
    var lastError: String? = null
) : BaseEntity()
