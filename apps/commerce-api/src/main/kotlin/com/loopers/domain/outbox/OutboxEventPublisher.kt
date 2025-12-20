package com.loopers.domain.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Outbox 이벤트 발행자
 * 도메인 이벤트를 Outbox 테이블에 저장
 */
@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java)

    /**
     * 이벤트를 Outbox 테이블에 저장
     * 도메인 트랜잭션과 함께 원자적으로 저장됨
     */
    @Transactional
    fun publish(
        eventType: String,
        topic: String,
        partitionKey: String,
        payload: Any,
        aggregateType: String,
        aggregateId: Long,
    ) {
        try {
            val payloadJson = objectMapper.writeValueAsString(payload)

            val outboxEvent = OutboxEvent.create(
                eventType = eventType,
                topic = topic,
                partitionKey = partitionKey,
                payload = payloadJson,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
            )

            outboxEventRepository.save(outboxEvent)

            logger.debug(
                "Outbox 이벤트 저장: eventType=$eventType, aggregateType=$aggregateType, " +
                    "aggregateId=$aggregateId, partitionKey=$partitionKey",
            )
        } catch (e: Exception) {
            logger.error("Outbox 이벤트 저장 실패: eventType=$eventType", e)
            throw e
        }
    }
}
