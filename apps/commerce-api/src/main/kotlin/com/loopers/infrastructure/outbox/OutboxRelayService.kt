package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
import com.loopers.support.outbox.OutboxFailed
import com.loopers.support.outbox.OutboxFailedRepository
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.TopicResolver
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * OutboxRelayService - Outbox 메시지 Kafka 릴레이 비즈니스 로직
 *
 * - relayNewMessages(): 새 메시지 비동기 배치 발행
 * - retryFailedMessages(): 실패 메시지 비동기 배치 재시도
 * - 서킷브레이커 OPEN 시 조기 반환, 실패 시 자동 기록
 */
@Service
class OutboxRelayService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val outboxRepository: OutboxRepository,
    private val outboxCursorRepository: OutboxCursorRepository,
    private val outboxFailedRepository: OutboxFailedRepository,
    circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME)

    companion object {
        private const val CIRCUIT_BREAKER_NAME = "outbox-relay"
        private const val BATCH_SIZE = 100
        private const val RETRY_BATCH_SIZE = 50
        private const val BATCH_TIMEOUT_SECONDS = 10L
    }

    /**
     * 새 메시지를 Kafka로 비동기 배치 릴레이한다.
     * - 커서 이후의 메시지를 조회하여 비동기 배치 전송
     * - 실패한 메시지는 OutboxFailed로 이동
     * - 처리 완료 후 커서 갱신
     * - Circuit Breaker에 성공/실패 자동 기록
     *
     * @return RelayResult 성공/실패 카운트 및 마지막 처리 ID
     */
    fun relayNewMessages(): RelayResult {
        if (isCircuitBreakerOpen()) {
            return RelayResult(successCount = 0, failedCount = 0, lastProcessedId = 0L)
        }

        val cursor = outboxCursorRepository.findLatest()
        val cursorId = cursor?.lastProcessedId ?: 0L
        val outboxMessages = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(cursorId, BATCH_SIZE)

        if (outboxMessages.isEmpty()) {
            return RelayResult(successCount = 0, failedCount = 0, lastProcessedId = cursorId)
        }

        // 비동기 배치 전송
        val futures = outboxMessages.map { outbox -> sendToKafkaAsync(outbox) }

        // 전체 대기
        CompletableFuture.allOf(*futures.toTypedArray())
            .orTimeout(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .handle { _, _ -> }
            .join()

        // 결과 집계
        val failedMessages = mutableListOf<OutboxFailed>()
        var successCount = 0
        var failedCount = 0

        futures.forEachIndexed { index, future ->
            val outbox = outboxMessages[index]
            if (future.isCompletedExceptionally || !future.isDone) {
                failedCount++
                val error = runCatching { future.getNow(Unit) }
                    .exceptionOrNull()?.message ?: "Timeout"
                failedMessages.add(OutboxFailed.from(outbox, error))
            } else {
                successCount++
            }
        }

        outboxFailedRepository.saveAll(failedMessages)
        outboxCursorRepository.save(OutboxCursor.create(outboxMessages.last().id))

        return RelayResult(
            successCount = successCount,
            failedCount = failedCount,
            lastProcessedId = outboxMessages.last().id,
        )
    }

    /**
     * 실패한 메시지를 비동기 배치 재시도한다.
     * - nextRetryAt이 현재 시각 이전인 메시지를 조회
     * - 성공 시 삭제, 실패 시 retryCount 증가
     * - Circuit Breaker에 성공/실패 자동 기록
     *
     * @return RetryResult 성공/실패 카운트
     */
    fun retryFailedMessages(): RetryResult {
        if (isCircuitBreakerOpen()) {
            return RetryResult(successCount = 0, failedCount = 0)
        }

        val retryableMessages = outboxFailedRepository.findRetryable(RETRY_BATCH_SIZE)

        if (retryableMessages.isEmpty()) {
            return RetryResult(successCount = 0, failedCount = 0)
        }

        // 비동기 배치 전송
        val futures = retryableMessages.map { failed -> sendFailedToKafkaAsync(failed) }

        // 전체 대기
        CompletableFuture.allOf(*futures.toTypedArray())
            .orTimeout(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .handle { _, _ -> }
            .join()

        // 결과 집계 및 처리
        val successMessages = mutableListOf<OutboxFailed>()
        val failedToRetry = mutableListOf<OutboxFailed>()

        futures.forEachIndexed { index, future ->
            val failed = retryableMessages[index]
            if (future.isCompletedExceptionally || !future.isDone) {
                val error = runCatching { future.getNow(Unit) }
                    .exceptionOrNull()?.message ?: "Timeout"
                failed.incrementRetryCount(error)
                failedToRetry.add(failed)
            } else {
                successMessages.add(failed)
            }
        }

        outboxFailedRepository.deleteAll(successMessages)
        outboxFailedRepository.saveAll(failedToRetry)

        return RetryResult(
            successCount = successMessages.size,
            failedCount = failedToRetry.size,
        )
    }

    private fun sendToKafkaAsync(outbox: Outbox): CompletableFuture<Unit> {
        val startTime = System.nanoTime()
        val topic = TopicResolver.resolve(outbox.eventType)
        val result = CompletableFuture<Unit>()

        kafkaTemplate.send(topic, outbox.aggregateId, outbox.payload)
            .whenComplete { _, ex ->
                val duration = System.nanoTime() - startTime
                if (ex != null) {
                    circuitBreaker.onError(duration, TimeUnit.NANOSECONDS, ex)
                    result.completeExceptionally(ex)
                } else {
                    circuitBreaker.onSuccess(duration, TimeUnit.NANOSECONDS)
                    result.complete(Unit)
                }
            }

        return result
    }

    private fun sendFailedToKafkaAsync(failed: OutboxFailed): CompletableFuture<Unit> {
        val startTime = System.nanoTime()
        val topic = TopicResolver.resolve(failed.eventType)
        val result = CompletableFuture<Unit>()

        kafkaTemplate.send(topic, failed.aggregateId, failed.payload)
            .whenComplete { _, ex ->
                val duration = System.nanoTime() - startTime
                if (ex != null) {
                    circuitBreaker.onError(duration, TimeUnit.NANOSECONDS, ex)
                    result.completeExceptionally(ex)
                } else {
                    circuitBreaker.onSuccess(duration, TimeUnit.NANOSECONDS)
                    result.complete(Unit)
                }
            }

        return result
    }

    private fun isCircuitBreakerOpen(): Boolean {
        return circuitBreaker.state == CircuitBreaker.State.OPEN
    }
}

/**
 * 릴레이 결과 DTO
 *
 * @property successCount 성공한 메시지 수
 * @property failedCount 실패한 메시지 수
 * @property lastProcessedId 마지막 처리된 Outbox ID
 */
data class RelayResult(
    val successCount: Int,
    val failedCount: Int,
    val lastProcessedId: Long,
)

/**
 * 재시도 결과 DTO
 *
 * @property successCount 성공한 메시지 수
 * @property failedCount 실패한 메시지 수
 */
data class RetryResult(
    val successCount: Int,
    val failedCount: Int,
)
