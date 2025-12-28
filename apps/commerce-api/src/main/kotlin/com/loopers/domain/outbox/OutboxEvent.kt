package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Transactional Outbox Pattern을 위한 이벤트 저장소
 * 도메인 이벤트를 DB에 저장한 후, 별도 Relay에서 Kafka로 발행
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status_created", columnList = "status,createdAt"),
        Index(name = "idx_outbox_aggregate", columnList = "aggregateType,aggregateId"),
    ],
)
class OutboxEvent(
    /**
     * 이벤트 타입 (e.g. OrderCreated, LikeAdded)
     */
    @Column(nullable = false, length = 100)
    val eventType: String,

    /**
     * Kafka 토픽
     */
    @Column(nullable = false, length = 100)
    val topic: String,

    /**
     * 파티션 키 (순서 보장을 위한 키, 예: productId, orderId)
     */
    @Column(nullable = false, length = 100)
    val partitionKey: String,

    /**
     * 이벤트 페이로드 (JSON)
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

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
     * 이벤트 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,

    /**
     * 재시도 횟수
     */
    @Column(nullable = false)
    var retryCount: Int = 0,

    /**
     * 마지막 시도 시각
     */
    @Column
    var lastAttemptAt: LocalDateTime? = null,

    /**
     * 에러 메시지
     */
    @Column(length = 1000)
    var errorMessage: String? = null,

    /**
     * 처리 완료 시각
     */
    @Column
    var publishedAt: LocalDateTime? = null,
) : BaseEntity() {

    /**
     * 발행 성공 처리
     */
    fun markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED
        this.publishedAt = LocalDateTime.now()
        this.lastAttemptAt = LocalDateTime.now()
    }

    /**
     * 발행 실패 처리
     */
    fun markAsFailed(errorMessage: String, maxRetryCount: Int) {
        this.errorMessage = errorMessage.take(1000) // 최대 1000자
        this.retryCount++
        this.lastAttemptAt = LocalDateTime.now()

        // 최대 재시도 횟수를 초과한 경우에만 FAILED 상태로 변경
        if (this.retryCount >= maxRetryCount) {
            this.status = OutboxEventStatus.FAILED
        }
        // 그 외의 경우 PENDING 상태 유지 (스케줄러가 재시도)
    }

    /**
     * 재시도 가능 여부 확인
     */
    fun canRetry(maxRetryCount: Int): Boolean = this.retryCount < maxRetryCount && this.status != OutboxEventStatus.PUBLISHED

    /**
     * 재시도 처리 시작
     */
    fun startRetry() {
        this.lastAttemptAt = LocalDateTime.now()
    }

    /**
     * 처리 중 상태로 변경
     */
    fun markAsProcessing() {
        this.status = OutboxEventStatus.PROCESSING
        this.lastAttemptAt = LocalDateTime.now()
    }

    /**
     * 처리 실패 후 대기 상태로 복원
     */
    fun resetToPending() {
        this.status = OutboxEventStatus.PENDING
    }

    companion object {
        /**
         * 새로운 Outbox 이벤트 생성
         */
        fun create(
            eventType: String,
            topic: String,
            partitionKey: String,
            payload: String,
            aggregateType: String,
            aggregateId: Long,
        ): OutboxEvent = OutboxEvent(
                eventType = eventType,
                topic = topic,
                partitionKey = partitionKey,
                payload = payload,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
            )
    }
}

/**
 * Outbox 이벤트 상태
 */
enum class OutboxEventStatus {
    /**
     * 발행 대기 중
     */
    PENDING,

    /**
     * 처리 중
     */
    PROCESSING,

    /**
     * 발행 완료
     */
    PUBLISHED,

    /**
     * 발행 실패 (최대 재시도 횟수 초과)
     */
    FAILED,
}
