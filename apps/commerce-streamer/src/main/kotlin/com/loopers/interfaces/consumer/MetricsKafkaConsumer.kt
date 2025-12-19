package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.MetricsEventFacade
import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.coupon.CouponUsedEvent
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.event.payment.PaymentCompletedEvent
import com.loopers.domain.event.payment.PaymentFailedEvent
import com.loopers.domain.event.product.StockDecreasedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Kafka Consumer (Kafka 수신 전용)
 * - Manual Ack
 * - 이벤트 파싱 및 위임
 */
@Component
class MetricsKafkaConsumer(
    private val metricsEventFacade: MetricsEventFacade,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        groupId = "metrics-consumer-group",
        containerFactory = "manualAckKafkaListenerContainerFactory",
    )
    @Transactional
    fun consume(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        logger.info("메세지 수신: partition=$partition, offset=$offset, key=$key")

        try {
            // 1. JSON 파싱
            val event = parseEvent(message)

            // 2. Facade 에 위임 (멱등성 체크  + 이벤트 처리 + 동기화)
            metricsEventFacade.handleEvent(event)

            // 3. Manual Ack
            acknowledgment.acknowledge()
            logger.info("이벤트 처리 완료: eventId=${event.eventId}, eventType=${event.eventType}")

        } catch (e: Exception) {
            logger.error("이벤트 처리 실패: partition=$partition, offset=$offset, error=${e.message}", e)
            // ACK 하지 않음 → 재처리됨
        }
    }

    /**
     * JSON 메시지 파싱
     * - eventType 기반으로 적절한 이벤트 클래스로 역직렬화
     */
    private fun parseEvent(message: String): DomainEvent {
        val node = objectMapper.readTree(message)
        val eventType = node["eventType"].asText()

        return when (eventType) {
            // catalog-events
            "PRODUCT_LIKED" -> objectMapper.readValue(message, ProductLikedEvent::class.java)
            "PRODUCT_UNLIKED" -> objectMapper.readValue(message, ProductUnlikedEvent::class.java)
            "PRODUCT_VIEWED" -> objectMapper.readValue(message, ProductViewedEvent::class.java)
            "STOCK_DECREASED" -> objectMapper.readValue(message, StockDecreasedEvent::class.java)

            // order-events
            "ORDER_CREATED" -> objectMapper.readValue(message, OrderCreatedEvent::class.java)
            "PAYMENT_COMPLETED" -> objectMapper.readValue(message, PaymentCompletedEvent::class.java)
            "PAYMENT_FAILED" -> objectMapper.readValue(message, PaymentFailedEvent::class.java)
            "COUPON_USED" -> objectMapper.readValue(message, CouponUsedEvent::class.java)

            else -> throw IllegalArgumentException("Unknown event type: $eventType")
        }
    }

}
