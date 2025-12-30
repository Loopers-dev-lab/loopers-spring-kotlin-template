package com.loopers.infrastructure.kafka

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Outbox 이벤트 프로세서
 * 개별 이벤트를 독립적인 트랜잭션으로 처리
 */
@Service
@ConditionalOnProperty(
    prefix = "kafka.outbox.relay",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
@ConditionalOnBean(KafkaProducerService::class)
class OutboxEventProcessor(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaProducerService: KafkaProducerService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OutboxEventProcessor::class.java)
    }

    @Value("\${kafka.outbox.relay.max-retry-count:3}")
    private var maxRetryCount: Int = 3

    /**
     * 개별 이벤트 처리
     * 각 이벤트는 독립적인 트랜잭션으로 처리
     */
    @Transactional
    fun processEvent(event: OutboxEvent): Boolean {
        return try {
            // 재시도 가능 여부 확인
            if (!event.canRetry(maxRetryCount)) {
                logger.warn(
                    "Outbox 이벤트 최대 재시도 횟수 초과: " +
                            "eventId=${event.id}, retryCount=${event.retryCount}",
                )
                // 최대 재시도 횟수 초과 시 FAILED 상태로 설정 및 저장
                event.markAsFailed("최대 재시도 횟수 초과", maxRetryCount)
                outboxEventRepository.save(event)
                return false
            }

            event.startRetry()

            // Kafka로 메시지 전송
            val future = kafkaProducerService.send(
                topic = event.topic,
                key = event.partitionKey,
                message = event.payload,
                eventType = event.eventType,
            )

            // 동기적으로 결과 대기 (타임아웃 5초)
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)

            // 성공 처리
            event.markAsPublished()
            outboxEventRepository.save(event)

            logger.debug(
                "Outbox 이벤트 발행 성공: eventId=${event.id}, " +
                        "eventType=${event.eventType}, topic=${event.topic}",
            )

            true
        } catch (e: Exception) {
            // 실패 처리
            event.markAsFailed(e.message ?: "알 수 없는 오류", maxRetryCount)

            // 재시도 가능하면 PENDING 상태로 복원
            if (event.canRetry(maxRetryCount)) {
                event.resetToPending()
            }

            outboxEventRepository.save(event)

            logger.error(
                "Outbox 이벤트 발행 실패: eventId=${event.id}, " +
                        "eventType=${event.eventType}, retryCount=${event.retryCount}, " +
                        "status=${event.status}",
                e,
            )

            false
        }
    }
}
