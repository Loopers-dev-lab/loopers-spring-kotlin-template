package com.loopers.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.event.LikeRemovedEvent
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.product.ProductMetricsRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Kafka 이벤트 Consumer
 * catalog-events, order-events 토픽에서 메시지를 소비하여 집계 처리
 */
@Component
@ConditionalOnBean(KafkaTemplate::class)
class KafkaEventConsumer(
    private val objectMapper: ObjectMapper,
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricsRepository: ProductMetricsRepository,
) {
    private val logger = LoggerFactory.getLogger(KafkaEventConsumer::class.java)

    /**
     * catalog-events 토픽 리스너
     * LikeAddedEvent, LikeRemovedEvent 등을 처리
     */
    @KafkaListener(
        topics = ["\${kafka.topics.catalog-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    @Transactional
    fun consumeCatalogEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        @Header("eventType", required = false) eventType: String?,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.debug("catalog-events 메시지 수신: key=$key, eventType=$eventType")

            // 이벤트 타입에 따라 분기 처리
            when (eventType) {
                "LikeAddedEvent" -> handleLikeAdded(message, acknowledgment)
                "LikeRemovedEvent" -> handleLikeRemoved(message, acknowledgment)
                else -> {
                    logger.warn("알 수 없는 이벤트 타입: $eventType")
                    acknowledgment.acknowledge()
                }
            }
        } catch (e: Exception) {
            logger.error("catalog-events 메시지 처리 실패: key=$key", e)
            // 에러 발생 시 DLQ로 전송 또는 재시도 로직 필요
            throw e
        }
    }

    /**
     * order-events 토픽 리스너
     * OrderCreatedEvent 등을 처리
     */
    @KafkaListener(
        topics = ["\${kafka.topics.order-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    @Transactional
    fun consumeOrderEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        @Header("eventType", required = false) eventType: String?,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.debug("order-events 메시지 수신: key=$key, eventType=$eventType")

            when (eventType) {
                "OrderCreatedEvent" -> handleOrderCreated(message, acknowledgment)
                else -> {
                    logger.warn("알 수 없는 이벤트 타입: $eventType")
                    acknowledgment.acknowledge()
                }
            }
        } catch (e: Exception) {
            logger.error("order-events 메시지 처리 실패: key=$key", e)
            throw e
        }
    }

    /**
     * LikeAddedEvent 처리
     */
    private fun handleLikeAdded(message: String, acknowledgment: Acknowledgment) {
        val event: LikeAddedEvent = objectMapper.readValue(message)

        // 멱등성 체크
        if (isAlreadyHandled(
                "LikeAddedEvent",
                "Product",
                event.productId,
                event.createdAt.nano.toLong(),
            )
        ) {
            logger.debug("이미 처리된 이벤트: LikeAddedEvent, productId=${event.productId}")
            acknowledgment.acknowledge()
            return
        }

        // 집계 처리
        val metrics = productMetricsRepository.findOrCreateByProductId(event.productId)
        metrics.incrementLikeCount()
        productMetricsRepository.save(metrics)

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(
                eventType = "LikeAddedEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
                eventVersion = event.createdAt.nano.toLong(),
            ),
        )

        acknowledgment.acknowledge()
        logger.info("LikeAddedEvent 처리 완료: productId=${event.productId}, likeCount=${metrics.likeCount}")
    }

    /**
     * LikeRemovedEvent 처리
     */
    private fun handleLikeRemoved(message: String, acknowledgment: Acknowledgment) {
        val event: LikeRemovedEvent = objectMapper.readValue(message)

        if (isAlreadyHandled(
                "LikeRemovedEvent",
                "Product",
                event.productId,
                event.createdAt.nano.toLong(),
            )
        ) {
            logger.debug("이미 처리된 이벤트: LikeRemovedEvent, productId=${event.productId}")
            acknowledgment.acknowledge()
            return
        }

        val metrics = productMetricsRepository.findOrCreateByProductId(event.productId)
        metrics.decrementLikeCount()
        productMetricsRepository.save(metrics)

        eventHandledRepository.save(
            EventHandled.create(
                eventType = "LikeRemovedEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
                eventVersion = event.createdAt.nano.toLong(),
            ),
        )

        acknowledgment.acknowledge()
        logger.info("LikeRemovedEvent 처리 완료: productId=${event.productId}, likeCount=${metrics.likeCount}")
    }

    /**
     * OrderCreatedEvent 처리
     */
    private fun handleOrderCreated(message: String, acknowledgment: Acknowledgment) {
        val event: OrderCreatedEvent = objectMapper.readValue(message)

        val eventVersion = event.createdAt.toEpochSecond()

        if (isAlreadyHandled(
                "OrderCreatedEvent",
                "Order",
                event.orderId,
                eventVersion,
            )
        ) {
            logger.debug("이미 처리된 이벤트: OrderCreatedEvent, orderId=${event.orderId}")
            acknowledgment.acknowledge()
            return
        }

        // 주문 관련 집계 처리 (판매량 등)
        // TODO: 주문 상품 정보를 통해 각 상품의 판매량 집계

        eventHandledRepository.save(
            EventHandled.create(
                eventType = "OrderCreatedEvent",
                aggregateType = "Order",
                aggregateId = event.orderId,
                eventVersion = eventVersion,
            ),
        )

        acknowledgment.acknowledge()
        logger.info("OrderCreatedEvent 처리 완료: orderId=${event.orderId}")
    }

    /**
     * 이미 처리된 이벤트인지 확인 (멱등성 보장)
     */
    private fun isAlreadyHandled(
        eventType: String,
        aggregateType: String,
        aggregateId: Long,
        eventVersion: Long,
    ): Boolean {
        return eventHandledRepository.existsByEventKey(
            eventType = eventType,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventVersion = eventVersion,
        )
    }
}
