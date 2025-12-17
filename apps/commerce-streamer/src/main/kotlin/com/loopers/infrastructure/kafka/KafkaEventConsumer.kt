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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
                    acknowledgeAfterCommit(acknowledgment)
                }
            }
        } catch (e: Exception) {
            logger.error("catalog-events 메시지 처리 실패: key=$key", e)
            // DefaultErrorHandler가 재시도 후 DLQ로 라우팅 처리
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
                    acknowledgeAfterCommit(acknowledgment)
                }
            }
        } catch (e: Exception) {
            logger.error("order-events 메시지 처리 실패: key=$key", e)
            // DefaultErrorHandler가 재시도 후 DLQ로 라우팅 처리
            throw e
        }
    }

    /**
     * LikeAddedEvent 처리
     */
    private fun handleLikeAdded(message: String, acknowledgment: Acknowledgment) {
        val event: LikeAddedEvent = objectMapper.readValue(message)

        // 멱등성 체크
        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: LikeAddedEvent, eventId=${event.eventId}, productId=${event.productId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 집계 처리 (비관적 락 사용)
        val metrics = productMetricsRepository.findOrCreateByProductIdWithLock(event.productId)
        metrics.incrementLikeCount()
        productMetricsRepository.save(metrics)

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "LikeAddedEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "LikeAddedEvent 처리 완료: eventId=${event.eventId}, productId=${event.productId}, likeCount=${metrics.likeCount}",
        )
    }

    /**
     * LikeRemovedEvent 처리
     */
    private fun handleLikeRemoved(message: String, acknowledgment: Acknowledgment) {
        val event: LikeRemovedEvent = objectMapper.readValue(message)

        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: LikeRemovedEvent, eventId=${event.eventId}, productId=${event.productId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 집계 처리 (비관적 락 사용)
        val metrics = productMetricsRepository.findOrCreateByProductIdWithLock(event.productId)
        metrics.decrementLikeCount()
        productMetricsRepository.save(metrics)

        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "LikeRemovedEvent",
                aggregateType = "Product",
                aggregateId = event.productId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "LikeRemovedEvent 처리 완료: eventId=${event.eventId}, productId=${event.productId}, likeCount=${metrics.likeCount}",
        )
    }

    /**
     * OrderCreatedEvent 처리
     */
    private fun handleOrderCreated(message: String, acknowledgment: Acknowledgment) {
        val event: OrderCreatedEvent = objectMapper.readValue(message)

        if (isAlreadyHandled(event.eventId)) {
            logger.debug("이미 처리된 이벤트: OrderCreatedEvent, eventId=${event.eventId}, orderId=${event.orderId}")
            acknowledgeAfterCommit(acknowledgment)
            return
        }

        // 주문 상품별 판매량 집계 (비관적 락 사용)
        event.items.forEach { item ->
            val metrics = productMetricsRepository.findOrCreateByProductIdWithLock(item.productId)
            val totalAmount = item.priceAtOrder * item.quantity
            metrics.incrementSales(
                quantity = item.quantity.toLong(),
                amount = totalAmount,
            )
            productMetricsRepository.save(metrics)

            logger.debug(
                "상품 판매량 집계 완료: productId=${item.productId}, " +
                    "quantity=${item.quantity}, amount=$totalAmount, " +
                    "totalSalesCount=${metrics.salesCount}, totalSalesAmount=${metrics.totalSalesAmount}",
            )
        }

        eventHandledRepository.save(
            EventHandled.create(
                eventId = event.eventId,
                eventType = "OrderCreatedEvent",
                aggregateType = "Order",
                aggregateId = event.orderId,
            ),
        )

        acknowledgeAfterCommit(acknowledgment)
        logger.info(
            "OrderCreatedEvent 처리 완료: eventId=${event.eventId}, orderId=${event.orderId}, " +
                "items=${event.items.size}개 상품 판매량 집계",
        )
    }

    /**
     * 이미 처리된 이벤트인지 확인 (멱등성 보장)
     */
    private fun isAlreadyHandled(eventId: java.util.UUID): Boolean {
        return eventHandledRepository.existsByEventId(eventId)
    }

    /**
     * DB 트랜잭션 커밋 후 Kafka offset을 acknowledge
     * 이를 통해 DB 커밋 실패 시 메시지 손실을 방지
     */
    private fun acknowledgeAfterCommit(acknowledgment: Acknowledgment) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    acknowledgment.acknowledge()
                }
            },
        )
    }
}
