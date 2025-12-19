package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.cache.CacheInvalidationFacade
import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.product.StockDecreasedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * 캐시 무효화 전용 Kafka Consumer
 * - StockDecreasedEvent 수신 시 상품 캐시 무효화
 * - metrics-consumer-group과 분리된 별도 그룹
 */
@Component
class CacheInvalidationKafkaConsumer(
    private val cacheInvalidationFacade: CacheInvalidationFacade,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events"],
        groupId = "cache-invalidation-consumer-group",
        containerFactory = "manualAckKafkaListenerContainerFactory"
    )
    fun consume(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        logger.info("캐시 무효화 메시지 수신: partition=$partition, offset=$offset, key=$key")

        try {
            val event = parseEvent(message)

            // 비대상 이벤트는 ACK만 하고 스킵
            if (event == null) {
                acknowledgment.acknowledge()
                logger.debug("비대상 이벤트 스킵: partition=$partition, offset=$offset")
                return
            }

            // 캐시 무효화 처리
            cacheInvalidationFacade.handleEvent(event)

            // Manual Ack
            acknowledgment.acknowledge()
            logger.info("캐시 무효화 처리 완료: eventId=${event.eventId}, eventType=${event.eventType}")

        } catch (e: Exception) {
            logger.error("캐시 무효화 처리 실패: partition=$partition, offset=$offset, error=${e.message}", e)
            // ACK 하지 않음 → 재처리됨
        }
    }

    /**
     * JSON 메시지 파싱
     * - StockDecreasedEvent만 처리
     * - 비대상 이벤트는 null 반환 (무한 재시도 방지)
     */
    private fun parseEvent(message: String): DomainEvent? {
        val node = objectMapper.readTree(message)
        val eventType = node["eventType"]?.asText()
            ?: throw IllegalArgumentException("Missing eventType in message: $message")

        return when (eventType) {
            "STOCK_DECREASED" -> objectMapper.readValue(message, StockDecreasedEvent::class.java)
            else -> {
                // 다른 이벤트는 무시 - null 반환하여 ACK 처리
                logger.debug("캐시 무효화 대상 아님: eventType=$eventType")
                null
            }
        }
    }
}
