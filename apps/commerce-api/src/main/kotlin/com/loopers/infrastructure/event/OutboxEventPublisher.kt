package com.loopers.infrastructure.event

import com.loopers.domain.event.EventOutbox
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Outbox 이벤트를 Kafka로 발행
 * - 1초마다 미처리 이벤트 조회
 * - Kafka 발행 성공 시 processed = true
 * - 실패 시 재시도 (최대 3회)
 */
@Component
class OutboxEventPublisher(
    private val eventOutboxRepository: EventOutboxJpaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val deadLetterQueueService: DeadLetterQueueService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_RETRY = 3
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun publishPendingEvents() {
        val pendingEvents = eventOutboxRepository
            .findTop100ByProcessedFalseOrderByCreatedAtAsc()

        if (pendingEvents.isEmpty()) {
            return
        }

        logger.info("미처리 이벤트 ${pendingEvents.size}개 발행 시작")

        pendingEvents.forEach { outbox ->
            try {
                publishToKafka(outbox)

                outbox.processed = true
                outbox.processedAt = Instant.now()
                eventOutboxRepository.save(outbox)

                logger.info("Kafka 발행 성공: eventId=${outbox.eventId}, partition=${outbox.kafkaPartition}, offset=${outbox.kafkaOffset}")
            } catch (e: Exception) {
                handlePublishFailure(outbox, e)
            }
        }
    }

    private fun publishToKafka(outbox: EventOutbox) {
        val topic = when (outbox.aggregateType.lowercase()) {
            "product" -> "catalog-events"
            "order" -> "order-events"
            else -> "general-events"
        }

        val partitionKey = outbox.aggregateId.toString()
        val result = kafkaTemplate.send(topic, partitionKey, outbox.payload).get(30, TimeUnit.SECONDS)

        outbox.kafkaPartition = result.recordMetadata.partition()
        outbox.kafkaOffset = result.recordMetadata.offset()
    }

    private fun handlePublishFailure(outbox: EventOutbox, e: Exception) {
        outbox.retryCount++
        outbox.lastError = e.message?.take(500)

        if (outbox.retryCount >= MAX_RETRY) {
            logger.error(
                "Kafka 발행 실패 (최대 재시도 초과): eventId=${outbox.eventId}, retryCount=${outbox.retryCount}", e
            )
            deadLetterQueueService.moveToDeadLetterQueue(outbox, e)

            outbox.processed = true
            outbox.processedAt = Instant.now()
        } else {
            logger.warn(
                "Kafka 발행 실패 (재시도 ${outbox.retryCount}회): eventId=${outbox.eventId}", e
            )
        }
        eventOutboxRepository.save(outbox)
    }
}
