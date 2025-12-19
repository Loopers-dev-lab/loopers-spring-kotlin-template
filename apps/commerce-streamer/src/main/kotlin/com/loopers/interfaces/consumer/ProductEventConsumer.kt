package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.OrderItemSnapshot
import com.loopers.domain.product.ProductStatisticService
import com.loopers.infrastructure.product.ProductCacheRepository
import com.loopers.interfaces.consumer.dto.CloudEventEnvelope
import com.loopers.interfaces.consumer.dto.LikeCanceledEventPayload
import com.loopers.interfaces.consumer.dto.LikeCreatedEventPayload
import com.loopers.interfaces.consumer.dto.OrderPaidEventPayload
import com.loopers.interfaces.consumer.dto.ProductViewedEventPayload
import com.loopers.interfaces.consumer.dto.StockDepletedEventPayload
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * ProductEventConsumer - 상품 관련 이벤트를 수신하여 처리하는 Kafka Consumer
 *
 * - 구독 토픽: like-events, order-events, product-view-events, stock-events
 * - 이벤트별 처리:
 *   - LikeCreatedEventV1: 좋아요 수 증가 (멱등성 X)
 *   - LikeCanceledEventV1: 좋아요 수 감소 (멱등성 X)
 *   - OrderPaidEventV1: 판매량 증가 (멱등성 O)
 *   - ProductViewedEventV1: 조회수 증가 (멱등성 O)
 *   - StockDepletedEventV1: 상품 캐시 무효화 (멱등성 X)
 */
@Component
class ProductEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productCacheRepository: ProductCacheRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["like-events", "order-events", "product-view-events", "stock-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        messages: List<ConsumerRecord<String, String>>,
        acknowledgment: Acknowledgment,
    ) {
        messages.forEach { record ->
            try {
                processMessage(record)
            } catch (e: Exception) {
                log.error(
                    "Failed to process message: topic={}, partition={}, offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    e,
                )
                throw e // DefaultErrorHandler가 재시도 후 DLQ 전송
            }
        }
        acknowledgment.acknowledge()
    }

    private fun processMessage(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)

        when (envelope.type) {
            "loopers.like.created.v1" -> handleLikeCreated(envelope)
            "loopers.like.canceled.v1" -> handleLikeCanceled(envelope)
            "loopers.order.paid.v1" -> handleOrderPaid(envelope)
            "loopers.product.viewed.v1" -> handleProductViewed(envelope)
            "loopers.stock.depleted.v1" -> handleStockDepleted(envelope)
            else -> log.warn("Unknown event type: {}", envelope.type)
        }
    }

    /** 좋아요 등록 - 멱등성 체크 불필요 (도메인 자체 멱등) */
    private fun handleLikeCreated(envelope: CloudEventEnvelope) {
        val event = objectMapper.readValue(envelope.payload, LikeCreatedEventPayload::class.java)
        productStatisticService.increaseLikeCount(event.productId)
    }

    /** 좋아요 취소 - 멱등성 체크 불필요 (도메인 자체 멱등) */
    private fun handleLikeCanceled(envelope: CloudEventEnvelope) {
        val event = objectMapper.readValue(envelope.payload, LikeCanceledEventPayload::class.java)
        productStatisticService.decreaseLikeCount(event.productId)
    }

    /** 주문 결제 완료 - 멱등성 체크 필요 */
    private fun handleOrderPaid(envelope: CloudEventEnvelope) {
        val aggregateType = "Order"
        val aggregateId = envelope.aggregateId
        val action = "SALES_COUNT_INCREASED"

        if (isAlreadyHandled(aggregateType, aggregateId, action)) {
            log.debug("Event already handled: type={}, id={}, action={}", aggregateType, aggregateId, action)
            return
        }

        val event = objectMapper.readValue(envelope.payload, OrderPaidEventPayload::class.java)
        val orderItems = event.orderItems.map { OrderItemSnapshot(it.productId, it.quantity) }

        productStatisticService.increaseSalesCount(orderItems)
        markAsHandled(aggregateType, aggregateId, action)
    }

    /** 상품 조회 - 멱등성 체크 필요 */
    private fun handleProductViewed(envelope: CloudEventEnvelope) {
        val aggregateType = "ProductView"
        val aggregateId = "${envelope.aggregateId}_${envelope.id}" // productId_eventId
        val action = "VIEW_COUNT_INCREASED"

        if (isAlreadyHandled(aggregateType, aggregateId, action)) {
            log.debug("Event already handled: type={}, id={}, action={}", aggregateType, aggregateId, action)
            return
        }

        val event = objectMapper.readValue(envelope.payload, ProductViewedEventPayload::class.java)

        productStatisticService.increaseViewCount(event.productId)
        markAsHandled(aggregateType, aggregateId, action)
    }

    /** 재고 소진 - 멱등성 체크 불필요 (동작 자체 멱등) */
    private fun handleStockDepleted(envelope: CloudEventEnvelope) {
        val event = objectMapper.readValue(envelope.payload, StockDepletedEventPayload::class.java)
        productCacheRepository.evictProductCache(event.productId)
    }

    private fun isAlreadyHandled(
        aggregateType: String,
        aggregateId: String,
        action: String,
    ): Boolean = eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(aggregateType, aggregateId, action)

    private fun markAsHandled(
        aggregateType: String,
        aggregateId: String,
        action: String,
    ) {
        eventHandledRepository.save(EventHandled.create(aggregateType, aggregateId, action))
    }
}
