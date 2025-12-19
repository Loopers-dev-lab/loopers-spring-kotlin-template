package com.loopers.infrastructure.event

import com.loopers.domain.event.DeadLetterQueue
import com.loopers.domain.event.EventOutbox
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DeadLetterQueueService(
    private val deadLetterQueueRepository: DeadLetterQueueRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun moveToDeadLetterQueue(outbox: EventOutbox, error: Exception) {
        val dlq = DeadLetterQueue(
            eventId = outbox.eventId,
            eventType = outbox.eventType,
            payload = outbox.payload,
            errorMessage = error.message ?: "Unknown error",
            stackTrace = error.stackTraceToString(),
            originalRetryCount = outbox.retryCount
        )

        deadLetterQueueRepository.save(dlq)
        logger.warn("이벤트 DLQ 이동: eventId=${outbox.eventId}, type=${outbox.eventType}")
    }

    @Transactional
    fun retryDeadLetterEvent(dlqId: Long, resolvedBy: String) : Boolean {
        val dlq = deadLetterQueueRepository.findById(dlqId).orElseThrow()

        if (dlq.processed) {
            logger.warn("이미 처리된 DLQ: id=$dlqId")
            return false
        }

        return try {
            val topic = getTopicForEventType(dlq.eventType)
            kafkaTemplate.send(topic, dlq.eventId, dlq.payload).get()

            dlq.processed = true
            dlq.processedAt = Instant.now()
            dlq.resolvedBy = resolvedBy
            dlq.resolution = "Manual retry successful"
            deadLetterQueueRepository.save(dlq)

            logger.info("DLQ 이벤트 재처리 성공: id=$dlqId, eventId=${dlq.eventId}")
            true
        } catch (e: Exception) {
            logger.error("DLQ 이벤트 재처리 실패: id=$dlqId", e)
            false
        }
    }

    private fun getTopicForEventType(eventType: String): String {
        return when {
            eventType.startsWith("product") -> "catalog-event"
            eventType.startsWith("order") -> "order-event"
            else -> "general-events"
        }
    }


    fun getUnprocessedDeadLetters(limit: Int = 100): List<DeadLetterQueue> {
        return deadLetterQueueRepository.findTop100ByProcessedFalseOrderByCreatedAtAsc()
    }

}
