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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * OutboxRelayService - Outbox 메시지 Kafka 릴레이 비즈니스 로직
 *
 * - relayNewMessages(): 새 메시지 비동기 배치 발행
 * - retryFailedMessages(): 실패 메시지 비동기 배치 재시도
 *
 * 전부 실패 시 커서 유지 + 지수 백오프 전략:
 * - 배치 전체가 실패하면 Kafka 장애로 간주
 * - 커서를 이동시키지 않고 지수 백오프 적용 (1s → 5s → 30s → 60s)
 * - OutboxFailed에 쌓이지 않아 불필요한 중복 방지
 * - 성공 시 백오프 카운터 리셋
 */
@Service
class OutboxRelayService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val outboxRepository: OutboxRepository,
    private val outboxCursorRepository: OutboxCursorRepository,
    private val outboxFailedRepository: OutboxFailedRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val BATCH_SIZE = 100
        private const val RETRY_BATCH_SIZE = 50
        private const val BATCH_TIMEOUT_SECONDS = 10L

        // 지수 백오프 설정: 1초 → 5초 → 30초 → 60초
        private val BACKOFF_DELAYS = listOf(1_000L, 5_000L, 30_000L, 60_000L)
    }

    // 백오프 상태 (스레드 안전성을 위해 @Volatile 사용)
    @Volatile
    private var consecutiveFailures = 0

    @Volatile
    private var nextAllowedTime = 0L

    /**
     * 새 메시지를 Kafka로 비동기 배치 릴레이한다.
     * - 커서 이후의 메시지를 조회하여 비동기 배치 전송
     * - 부분 성공: 성공분까지 커서 이동 + 실패분 OutboxFailed 저장
     * - 전부 실패: 커서 유지, 지수 백오프 적용 (다음 스케줄에서 재시도)
     *
     * @return RelayResult 성공/실패 카운트 및 마지막 처리 ID
     */
    @Transactional
    fun relayNewMessages(): RelayResult {
        val now = System.currentTimeMillis()

        // 백오프 대기 중이면 스킵
        if (now < nextAllowedTime) {
            val remainingMs = nextAllowedTime - now
            log.debug("[OutboxRelay] 백오프 대기 중. {}ms 남음", remainingMs)
            return RelayResult(successCount = 0, failedCount = 0, lastProcessedId = 0L, skipped = true)
        }

        val cursor = outboxCursorRepository.findLatest()
        val cursorId = cursor?.lastProcessedId ?: 0L
        val outboxMessages = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(cursorId, BATCH_SIZE)

        if (outboxMessages.isEmpty()) {
            resetBackoff()
            return RelayResult(successCount = 0, failedCount = 0, lastProcessedId = cursorId)
        }

        // 비동기 배치 전송 (각 future에 개별 타임아웃 적용됨)
        val futures = outboxMessages.map { outbox -> sendToKafkaAsync(outbox) }

        // 전체 대기 (개별 타임아웃이 적용되어 모든 future는 확정적으로 완료됨)
        CompletableFuture.allOf(*futures.toTypedArray())
            .handle { _, _ -> }
            .join()

        // 결과 집계
        val successMessages = mutableListOf<Outbox>()
        val failedMessages = mutableListOf<Outbox>()

        futures.forEachIndexed { index, future ->
            val outbox = outboxMessages[index]
            if (future.isCompletedExceptionally) {
                failedMessages.add(outbox)
            } else {
                successMessages.add(outbox)
            }
        }

        // 전부 실패: 커서 유지 + 백오프 적용 (Kafka 장애로 간주)
        if (successMessages.isEmpty()) {
            applyBackoff()
            return RelayResult(successCount = 0, failedCount = failedMessages.size, lastProcessedId = cursorId)
        }

        // 부분/전체 성공: 백오프 리셋 + 실패분 OutboxFailed 저장 + 커서 이동
        resetBackoff()
        val outboxFailedList = failedMessages.map { outbox ->
            val error = extractError(futures[outboxMessages.indexOf(outbox)])
            OutboxFailed.from(outbox, error)
        }

        outboxFailedRepository.saveAll(outboxFailedList)
        outboxCursorRepository.save(OutboxCursor.create(outboxMessages.last().id))

        return RelayResult(
            successCount = successMessages.size,
            failedCount = failedMessages.size,
            lastProcessedId = outboxMessages.last().id,
        )
    }

    /**
     * 실패한 메시지를 비동기 배치 재시도한다.
     * - nextRetryAt이 현재 시각 이전인 메시지를 조회
     * - 성공 시 삭제, 실패 시 retryCount 증가
     *
     * @return RetryResult 성공/실패 카운트
     */
    @Transactional
    fun retryFailedMessages(): RetryResult {
        val retryableMessages = outboxFailedRepository.findRetryable(RETRY_BATCH_SIZE)

        if (retryableMessages.isEmpty()) {
            return RetryResult(successCount = 0, failedCount = 0)
        }

        // 비동기 배치 전송 (각 future에 개별 타임아웃 적용됨)
        val futures = retryableMessages.map { failed -> sendFailedToKafkaAsync(failed) }

        // 전체 대기 (개별 타임아웃이 적용되어 모든 future는 확정적으로 완료됨)
        CompletableFuture.allOf(*futures.toTypedArray())
            .handle { _, _ -> }
            .join()

        // 결과 집계 및 처리
        val successMessages = mutableListOf<OutboxFailed>()
        val failedToRetry = mutableListOf<OutboxFailed>()

        futures.forEachIndexed { index, future ->
            val failed = retryableMessages[index]
            if (future.isCompletedExceptionally) {
                val error = extractError(future)
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

        return result.orTimeout(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun sendFailedToKafkaAsync(failed: OutboxFailed): CompletableFuture<Unit> {
        val topic = TopicResolver.resolve(failed.eventType)
        val result = CompletableFuture<Unit>()

        kafkaTemplate.send(topic, failed.aggregateId, failed.payload)
            .whenComplete { _, ex ->
                if (ex != null) {
                    result.completeExceptionally(ex)
                } else {
                    result.complete(Unit)
                }
            }

        return result.orTimeout(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun extractError(future: CompletableFuture<Unit>): String {
        return try {
            future.join()
            "Unknown error"
        } catch (e: Exception) {
            when (val cause = e.cause ?: e) {
                is java.util.concurrent.TimeoutException -> "Timeout"
                else -> cause.message ?: "Unknown error"
            }
        }
    }

    /**
     * 지수 백오프를 적용한다.
     * 연속 실패 횟수에 따라 대기 시간 증가: 1초 → 5초 → 30초 → 60초
     */
    private fun applyBackoff() {
        consecutiveFailures++
        val index = minOf(consecutiveFailures - 1, BACKOFF_DELAYS.lastIndex)
        val delay = BACKOFF_DELAYS[index]
        nextAllowedTime = System.currentTimeMillis() + delay

        log.warn("[OutboxRelay] 연속 {}회 전체 실패. {}ms 후 재시도", consecutiveFailures, delay)
    }

    /**
     * 백오프 상태를 리셋한다.
     * 성공 시 호출하여 정상 상태로 복귀
     */
    private fun resetBackoff() {
        if (consecutiveFailures > 0) {
            log.info("[OutboxRelay] 백오프 해제. 연속 실패 {}회에서 복구", consecutiveFailures)
            consecutiveFailures = 0
            nextAllowedTime = 0L
        }
    }
}

/**
 * 릴레이 결과 DTO
 *
 * @property successCount 성공한 메시지 수
 * @property failedCount 실패한 메시지 수
 * @property lastProcessedId 마지막 처리된 Outbox ID
 * @property skipped 백오프로 인해 처리를 스킵했는지 여부
 */
data class RelayResult(
    val successCount: Int,
    val failedCount: Int,
    val lastProcessedId: Long,
    val skipped: Boolean = false,
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
