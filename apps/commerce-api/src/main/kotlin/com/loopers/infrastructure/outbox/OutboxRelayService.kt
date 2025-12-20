package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
import com.loopers.support.outbox.OutboxFailed
import com.loopers.support.outbox.OutboxFailedRepository
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.TopicResolver
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * OutboxRelayService - Outbox 메시지 Kafka 릴레이 비즈니스 로직
 *
 * - relay(): 순서 보장 Relay (Debezium 스타일)
 *
 * 순서 보장 전략:
 * - 실패한 메시지가 있으면 후속 메시지 발행 중단 (HOL blocking)
 * - 설정된 시간 경과 후 OutboxFailed로 이동하여 후속 메시지 진행
 */
@Service
class OutboxRelayService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val outboxRepository: OutboxRepository,
    private val outboxCursorRepository: OutboxCursorRepository,
    private val outboxFailedRepository: OutboxFailedRepository,
    private val properties: OutboxRelayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 메시지를 순서대로 Kafka로 릴레이한다.
     *
     * - 커서 이후 메시지를 batchSize만큼 조회
     * - 발행 가능한 메시지(nextRetryAt이 null이거나 현재 시각 이후)만 순차 처리
     * - 성공 시 커서 진행
     * - 실패 시:
     *   - 만료됨 → OutboxFailed로 저장 후 커서 진행
     *   - 재시도 가능 → markForRetry 후 중단 (HOL blocking)
     *
     * @return RelayResult 성공/실패 카운트 및 마지막 처리 ID
     */
    @Transactional
    fun relay(): RelayResult {
        val cursor = outboxCursorRepository.findLatest()
        val cursorId = cursor?.lastProcessedId ?: 0L
        val messages = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(cursorId, properties.batchSize)

        if (messages.isEmpty()) {
            return RelayResult.empty(cursorId)
        }

        val now = Instant.now()
        val retryInterval = Duration.ofSeconds(properties.retryIntervalSeconds)
        val maxAge = Duration.ofMinutes(properties.maxAgeMinutes)

        // 대기 중인 메시지를 만나면 중단 (순서 보장)
        val sendable = messages.takeWhile { canSendNow(it, now) }
        if (sendable.isEmpty()) {
            return RelayResult.empty(cursorId)
        }

        // 비동기 배치 전송
        val futures = sendable.map { outbox -> sendToKafkaAsync(outbox) }

        // 순차 확인
        var newCursor = cursorId
        var successCount = 0
        var failedCount = 0

        for ((index, future) in futures.withIndex()) {
            val message = sendable[index]
            try {
                future.get(properties.sendTimeoutSeconds, TimeUnit.SECONDS)
                newCursor = message.id
                successCount++
            } catch (e: Exception) {
                val error = e.cause?.message ?: e.message ?: "Unknown"
                if (message.isExpired(maxAge, now)) {
                    // 만료 → OutboxFailed로 저장 (커서만 진행)
                    outboxFailedRepository.save(OutboxFailed.from(message, error))
                    newCursor = message.id
                    failedCount++
                } else {
                    // 재시도 예약 후 중단
                    message.markForRetry(retryInterval, now)
                    outboxRepository.save(message)
                    break
                }
            }
        }

        // 커서 갱신
        if (newCursor > cursorId) {
            outboxCursorRepository.save(OutboxCursor.create(newCursor))
        }

        return RelayResult(successCount, failedCount, newCursor)
    }

    /**
     * 메시지를 지금 발송할 수 있는지 확인한다.
     *
     * @param message 확인할 Outbox 메시지
     * @param now 현재 시각
     * @return true면 발송 가능, false면 대기 필요
     */
    private fun canSendNow(message: Outbox, now: Instant): Boolean {
        return message.nextRetryAt == null || message.nextRetryAt!! <= now
    }

    private fun sendToKafkaAsync(outbox: Outbox): CompletableFuture<Unit> {
        val topic = TopicResolver.resolve(outbox.eventType)
        val result = CompletableFuture<Unit>()

        kafkaTemplate.send(topic, outbox.aggregateId, outbox.payload)
            .whenComplete { _, ex ->
                if (ex != null) {
                    result.completeExceptionally(ex)
                } else {
                    result.complete(Unit)
                }
            }

        return result.orTimeout(properties.sendTimeoutSeconds, TimeUnit.SECONDS)
    }
}

/**
 * 릴레이 결과 DTO
 *
 * @property successCount 성공한 메시지 수
 * @property failedCount 실패한 메시지 수 (OutboxFailed로 이동한 메시지)
 * @property lastProcessedId 마지막 처리된 Outbox ID
 */
data class RelayResult(
    val successCount: Int,
    val failedCount: Int,
    val lastProcessedId: Long,
) {
    /**
     * 처리된 메시지가 있는지 확인한다.
     *
     * @return true면 성공 또는 실패한 메시지가 있음
     */
    fun hasActivity(): Boolean = successCount > 0 || failedCount > 0

    companion object {
        /**
         * 빈 결과를 생성한다.
         *
         * @param cursorId 현재 커서 ID
         * @return 처리된 메시지가 없는 RelayResult
         */
        fun empty(cursorId: Long): RelayResult = RelayResult(0, 0, cursorId)
    }
}
