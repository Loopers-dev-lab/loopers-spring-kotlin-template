package com.loopers.support.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

/**
 * OutboxFailed 엔티티 - 발행 실패 메시지 저장소
 *
 * - BaseEntity를 상속하지 않음 (인프라 데이터)
 * - 재시도 횟수와 지수 백오프 지원
 * - Exponential backoff: 1초, 2초, 4초, ... 최대 5분 (300초)
 */
@Entity
@Table(name = "outbox_failed")
class OutboxFailed(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "event_id", nullable = false, length = 36)
    val eventId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "source", nullable = false, length = 100)
    val source: String,

    @Column(name = "aggregate_type", nullable = false, length = 100)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 255)
    val aggregateId: String,

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    val payload: String,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "failed_at", nullable = false)
    val failedAt: Instant,

    @Column(name = "next_retry_at", nullable = false)
    var nextRetryAt: Instant,
) {

    /**
     * 재시도 횟수를 증가시키고 다음 재시도 시각을 계산한다.
     * Exponential backoff: 2^retryCount 초, 최대 300초 (5분)
     */
    fun incrementRetryCount(error: String) {
        retryCount++
        lastError = error
        nextRetryAt = calculateNextRetryAt()
    }

    private fun calculateNextRetryAt(): Instant {
        val backoffSeconds = min(
            2.0.pow(retryCount.toDouble()).toLong(),
            MAX_BACKOFF_SECONDS,
        )
        return Instant.now().plusSeconds(backoffSeconds)
    }

    companion object {
        private const val MAX_BACKOFF_SECONDS = 300L // 5 minutes
        const val MAX_RETRY_COUNT = 10

        fun from(outbox: Outbox, error: String): OutboxFailed {
            val now = Instant.now()
            return OutboxFailed(
                eventId = outbox.eventId,
                eventType = outbox.eventType,
                source = outbox.source,
                aggregateType = outbox.aggregateType,
                aggregateId = outbox.aggregateId,
                payload = outbox.payload,
                retryCount = 0,
                lastError = error,
                failedAt = now,
                // Initial retry after 1 second (2^0)
                nextRetryAt = now.plusSeconds(1),
            )
        }
    }
}
