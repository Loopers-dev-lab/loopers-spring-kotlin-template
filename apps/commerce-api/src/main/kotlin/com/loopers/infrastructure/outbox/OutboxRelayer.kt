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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * OutboxRelayer - Outbox 테이블의 미발행 이벤트를 Kafka로 릴레이
 *
 * - relayNewMessages(): 500ms 주기로 새 메시지 발행
 * - retryFailedMessages(): 5000ms 주기로 실패 메시지 재시도
 * - 서킷브레이커 OPEN 시 스킵
 */
@Component
class OutboxRelayer(
    @Qualifier("stringKafkaTemplate")
    private val stringKafkaTemplate: KafkaTemplate<String, String>,
    private val outboxRepository: OutboxRepository,
    private val outboxCursorRepository: OutboxCursorRepository,
    private val outboxFailedRepository: OutboxFailedRepository,
    circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
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
     */
    @Scheduled(fixedDelay = 500)
    fun relayNewMessages() {
        if (isCircuitBreakerOpen()) {
            log.debug("[OutboxRelayer] 서킷브레이커 OPEN 상태, 릴레이 스킵")
            return
        }

        val cursor = outboxCursorRepository.findLatest()
        val cursorId = cursor?.lastProcessedId ?: 0L
        val outboxMessages = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(cursorId, BATCH_SIZE)

        if (outboxMessages.isEmpty()) {
            return
        }

        log.debug("[OutboxRelayer] 릴레이 시작: cursorId={}, messages={}", cursorId, outboxMessages.size)

        val failedMessages = mutableListOf<OutboxFailed>()
        var lastProcessedId = cursorId

        for (outbox in outboxMessages) {
            if (isCircuitBreakerOpen()) {
                log.warn("[OutboxRelayer] 서킷브레이커 OPEN 전환, 릴레이 중단 (lastProcessedId={})", lastProcessedId)
                break
            }

            val sendResult = sendToKafka(outbox)
            if (sendResult.isSuccess) {
                lastProcessedId = outbox.id
            } else {
                val error = sendResult.exceptionOrNull()?.message ?: "Unknown error"
                failedMessages.add(OutboxFailed.from(outbox, error))
                lastProcessedId = outbox.id
            }
        }

        // 실패 메시지 저장
        if (failedMessages.isNotEmpty()) {
            outboxFailedRepository.saveAll(failedMessages)
            log.warn("[OutboxRelayer] 실패 메시지 저장: count={}", failedMessages.size)
        }

        // 커서 갱신 (처리 시도한 마지막 메시지까지)
        if (lastProcessedId > cursorId) {
            outboxCursorRepository.save(OutboxCursor.create(lastProcessedId))
            log.debug("[OutboxRelayer] 커서 갱신: {} -> {}", cursorId, lastProcessedId)
        }
    }

    /**
     * 실패한 메시지를 재시도한다.
     * - nextRetryAt이 현재 시각 이전인 메시지를 조회
     * - 성공 시 삭제, 실패 시 retryCount 증가
     */
    @Scheduled(fixedDelay = 5000)
    fun retryFailedMessages() {
        if (isCircuitBreakerOpen()) {
            log.debug("[OutboxRelayer] 서킷브레이커 OPEN 상태, 재시도 스킵")
            return
        }

        val retryableMessages = outboxFailedRepository.findRetryable(RETRY_BATCH_SIZE)

        if (retryableMessages.isEmpty()) {
            return
        }

        log.debug("[OutboxRelayer] 재시도 시작: messages={}", retryableMessages.size)

        for (failed in retryableMessages) {
            if (isCircuitBreakerOpen()) {
                log.warn("[OutboxRelayer] 서킷브레이커 OPEN 전환, 재시도 중단")
                break
            }

            val sendResult = sendFailedToKafka(failed)
            if (sendResult.isSuccess) {
                outboxFailedRepository.delete(failed)
                log.debug("[OutboxRelayer] 재시도 성공, 삭제: eventId={}", failed.eventId)
            } else {
                val error = sendResult.exceptionOrNull()?.message ?: "Unknown error"
                failed.incrementRetryCount(error)
                outboxFailedRepository.save(failed)
                log.warn(
                    "[OutboxRelayer] 재시도 실패: eventId={}, retryCount={}, error={}",
                    failed.eventId,
                    failed.retryCount,
                    error,
                )
            }
        }
    }

    private fun sendToKafka(outbox: Outbox): Result<Unit> {
        return try {
            val topic = TopicResolver.resolve(outbox.eventType)
            val key = outbox.aggregateId
            val payload = outbox.payload

            val future = stringKafkaTemplate.send(topic, key, payload)
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
            log.error("[OutboxRelayer] Kafka 전송 타임아웃: eventId={}", outbox.eventId, e)
            Result.failure(e)
        } catch (e: Exception) {
            log.error("[OutboxRelayer] Kafka 전송 실패: eventId={}", outbox.eventId, e)
            Result.failure(e)
        }
    }

    private fun sendFailedToKafka(failed: OutboxFailed): Result<Unit> {
        return try {
            val topic = TopicResolver.resolve(failed.eventType)
            val key = failed.aggregateId
            val payload = failed.payload

            val future = stringKafkaTemplate.send(topic, key, payload)
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
            log.error("[OutboxRelayer] Kafka 재시도 타임아웃: eventId={}", failed.eventId, e)
            Result.failure(e)
        } catch (e: Exception) {
            log.error("[OutboxRelayer] Kafka 재시도 실패: eventId={}", failed.eventId, e)
            Result.failure(e)
        }
    }

    private fun isCircuitBreakerOpen(): Boolean {
        return circuitBreaker.state == CircuitBreaker.State.OPEN
    }
}
