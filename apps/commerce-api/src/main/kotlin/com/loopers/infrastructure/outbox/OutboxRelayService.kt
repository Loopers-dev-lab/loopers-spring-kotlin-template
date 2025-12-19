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
import java.util.concurrent.TimeoutException

/**
 * OutboxRelayService - Outbox 메시지 Kafka 릴레이 비즈니스 로직
 *
 * - relayNewMessages(): 새 메시지 발행
 * - retryFailedMessages(): 실패 메시지 재시도
 * - 서킷브레이커 OPEN 시 조기 반환
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
        private const val SEND_TIMEOUT_SECONDS = 30L
    }

    /**
     * 새 메시지를 Kafka로 릴레이한다.
     * - 커서 이후의 메시지를 조회하여 배치 전송
     * - 실패한 메시지는 OutboxFailed로 이동
     * - 처리 완료 후 커서 갱신
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

        var successCount = 0
        var failedCount = 0
        val failedMessages = mutableListOf<OutboxFailed>()
        var lastProcessedId = cursorId

        for (outbox in outboxMessages) {
            if (isCircuitBreakerOpen()) break

            val sendResult = sendToKafka(outbox)
            if (sendResult.isSuccess) {
                successCount++
            } else {
                failedCount++
                val error = sendResult.exceptionOrNull()?.message ?: "Unknown error"
                failedMessages.add(OutboxFailed.from(outbox, error))
            }
            lastProcessedId = outbox.id
        }

        if (failedMessages.isNotEmpty()) {
            outboxFailedRepository.saveAll(failedMessages)
        }

        if (lastProcessedId > cursorId) {
            outboxCursorRepository.save(OutboxCursor.create(lastProcessedId))
        }

        return RelayResult(
            successCount = successCount,
            failedCount = failedCount,
            lastProcessedId = lastProcessedId,
        )
    }

    /**
     * 실패한 메시지를 재시도한다.
     * - nextRetryAt이 현재 시각 이전인 메시지를 조회
     * - 성공 시 삭제, 실패 시 retryCount 증가
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

        var successCount = 0
        var failedCount = 0

        for (failed in retryableMessages) {
            if (isCircuitBreakerOpen()) break

            val sendResult = sendFailedToKafka(failed)
            if (sendResult.isSuccess) {
                outboxFailedRepository.delete(failed)
                successCount++
            } else {
                val error = sendResult.exceptionOrNull()?.message ?: "Unknown error"
                failed.incrementRetryCount(error)
                outboxFailedRepository.save(failed)
                failedCount++
            }
        }

        return RetryResult(
            successCount = successCount,
            failedCount = failedCount,
        )
    }

    private fun sendToKafka(outbox: Outbox): Result<Unit> {
        return try {
            val topic = TopicResolver.resolve(outbox.eventType)
            val key = outbox.aggregateId
            val payload = outbox.payload

            val future = kafkaTemplate.send(topic, key, payload)
            val completableFuture = CompletableFuture<Unit>()

            future.whenComplete { _, ex ->
                if (ex != null) {
                    completableFuture.completeExceptionally(ex)
                } else {
                    completableFuture.complete(Unit)
                }
            }

            completableFuture.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            Result.success(Unit)
        } catch (e: TimeoutException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendFailedToKafka(failed: OutboxFailed): Result<Unit> {
        return try {
            val topic = TopicResolver.resolve(failed.eventType)
            val key = failed.aggregateId
            val payload = failed.payload

            val future = kafkaTemplate.send(topic, key, payload)
            val completableFuture = CompletableFuture<Unit>()

            future.whenComplete { _, ex ->
                if (ex != null) {
                    completableFuture.completeExceptionally(ex)
                } else {
                    completableFuture.complete(Unit)
                }
            }

            completableFuture.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            Result.success(Unit)
        } catch (e: TimeoutException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
