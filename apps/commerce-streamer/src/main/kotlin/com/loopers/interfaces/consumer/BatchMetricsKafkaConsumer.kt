package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.BatchMetricsEventFacade
import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.coupon.CouponUsedEvent
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.event.payment.PaymentCompletedEvent
import com.loopers.domain.event.payment.PaymentFailedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.domain.event.product.StockDecreasedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BatchMetricsKafkaConsumer(
    private val batchMetricsEventFacade: BatchMetricsEventFacade,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        groupId = "metrics-consumer-group-batch",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    @Transactional
    fun consumeBatch(
        messages: List<ConsumerRecord<String, String>>,
        acknowledgment: Acknowledgment
    ) {
        logger.info("배치 메시지 수신: ${messages.size}개")

        try {
            val events = messages.mapNotNull { record ->
                try {
                    logger.debug("메시지 수신: key=${record.key()}, value=${record.value()}")
                    parseEvent(record.value())
                } catch (e: Exception) {
                    logger.error("개별 메시지 파싱 실패: key=${record.key()}, partition=${record.partition()}, offset=${record.offset()}", e)
                    null
                }
            }

            if (events.isEmpty()) {
                logger.warn("파싱 가능한 이벤트가 없음: 전체 ${messages.size}개 중 0개")
                acknowledgment.acknowledge() // 파싱 실패한 메시지는 건너뛰고 ACK
                return
            }

            batchMetricsEventFacade.handleBatchEvents(events)

            acknowledgment.acknowledge()
            logger.info("배치 처리 완료: ${events.size}개")
        } catch (e: Exception) {
            logger.error("배치 처리 실패: ${messages.size}개, error=${e.message}", e)
        }
    }

    private fun parseEvent(message: String): DomainEvent {
        try {
            logger.debug("파싱 시작: message=$message")
            val node = objectMapper.readTree(message)
            logger.debug("JSON 파싱 성공: node=$node")
            
            val eventTypeNode = node.get("eventType")
            logger.debug("eventTypeNode: $eventTypeNode")
            
            if (eventTypeNode == null || eventTypeNode.isNull) {
                logger.error("eventType 필드가 없음. 전체 노드: $node")
                throw IllegalArgumentException("Missing eventType in message: $message")
            }
            
            val eventType = eventTypeNode.asText()
            logger.debug("eventType 추출: $eventType")

            return when (eventType) {
            "PRODUCT_LIKED" -> objectMapper.readValue(message, ProductLikedEvent::class.java)
            "PRODUCT_UNLIKED" -> objectMapper.readValue(message, ProductUnlikedEvent::class.java)
            "PRODUCT_VIEWED" -> objectMapper.readValue(message, ProductViewedEvent::class.java)
            "STOCK_DECREASED" -> objectMapper.readValue(message, StockDecreasedEvent::class.java)
            "ORDER_CREATED" -> objectMapper.readValue(message, OrderCreatedEvent::class.java)
            "PAYMENT_COMPLETED" -> objectMapper.readValue(message, PaymentCompletedEvent::class.java)
            "PAYMENT_FAILED" -> objectMapper.readValue(message, PaymentFailedEvent::class.java)
            "COUPON_USED" -> objectMapper.readValue(message, CouponUsedEvent::class.java)

            else -> throw IllegalArgumentException("Unknown event type: $eventType")
            }
        } catch (e: Exception) {
            logger.error("이벤트 파싱 실패: message=$message", e)
            throw e
        }
    }
}
