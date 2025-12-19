package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.ProductStatisticService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.product.ProductCacheRepository
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * ProductEventConsumer - 상품 관련 이벤트를 배치 처리하는 Kafka Consumer
 *
 * - 구독 토픽: like-events, order-events, product-view-events, stock-events
 * - 배치 처리 방식:
 *   1. 메시지를 파싱하여 CloudEventEnvelope 변환 (실패 시 로그 후 건너뜀)
 *   2. 이벤트 타입별로 그룹화
 *   3. 타입별로 일괄 처리 (batch service methods 호출)
 *
 * - 멱등성 처리:
 *   - LikeCreated/LikeCanceled: 멱등성 체크 불필요 (도메인 자체 멱등)
 *   - OrderPaid: 멱등성 체크 필요 (Order 단위)
 *   - ProductViewed: 멱등성 체크 필요 (Product + EventId 단위)
 *   - StockDepleted: 멱등성 체크 불필요 (동작 자체 멱등)
 */
@Component
class ProductEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productCacheRepository: ProductCacheRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val productEventMapper: ProductEventMapper,
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
        val envelopes = messages.mapNotNull { record ->
            try {
                objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse message: topic={}, offset={}", record.topic(), record.offset(), e)
                null
            }
        }

        // Group by event type category
        val likeEvents = envelopes.filter { it.type in LIKE_EVENT_TYPES }
        val orderPaidEvents = envelopes.filter { it.type == "loopers.order.paid.v1" }
        val productViewEvents = envelopes.filter { it.type == "loopers.product.viewed.v1" }
        val stockDepletedEvents = envelopes.filter { it.type == "loopers.stock.depleted.v1" }

        try {
            handleLikeEvents(likeEvents)
            handleOrderPaidEvents(orderPaidEvents)
            handleProductViewedEvents(productViewEvents)
            handleStockDepletedEvents(stockDepletedEvents)
        } catch (e: Exception) {
            log.error("Failed to process batch", e)
            throw e
        }

        acknowledgment.acknowledge()
    }

    /** Like events - no idempotency check (domain handles it) */
    private fun handleLikeEvents(envelopes: List<CloudEventEnvelope>) {
        if (envelopes.isEmpty()) return
        val command = productEventMapper.toLikeCommand(envelopes)
        productStatisticService.updateLikeCount(command)
    }

    /** Order paid - idempotency check required */
    private fun handleOrderPaidEvents(envelopes: List<CloudEventEnvelope>) {
        if (envelopes.isEmpty()) return

        val aggregateType = "Order"
        val action = "SALES_COUNT_INCREASED"

        val unhandledEnvelopes = envelopes.filter { envelope ->
            !isAlreadyHandled(aggregateType, envelope.aggregateId, action)
        }

        if (unhandledEnvelopes.isEmpty()) return

        val command = productEventMapper.toSalesCommand(unhandledEnvelopes)
        productStatisticService.updateSalesCount(command)

        unhandledEnvelopes.forEach { envelope ->
            markAsHandled(aggregateType, envelope.aggregateId, action)
        }
    }

    /** Product viewed - idempotency check required */
    private fun handleProductViewedEvents(envelopes: List<CloudEventEnvelope>) {
        if (envelopes.isEmpty()) return

        val aggregateType = "ProductView"
        val action = "VIEW_COUNT_INCREASED"

        val unhandledEnvelopes = envelopes.filter { envelope ->
            val aggregateId = "${envelope.aggregateId}_${envelope.id}"
            !isAlreadyHandled(aggregateType, aggregateId, action)
        }

        if (unhandledEnvelopes.isEmpty()) return

        val command = productEventMapper.toViewCommand(unhandledEnvelopes)
        productStatisticService.updateViewCount(command)

        unhandledEnvelopes.forEach { envelope ->
            val aggregateId = "${envelope.aggregateId}_${envelope.id}"
            markAsHandled(aggregateType, aggregateId, action)
        }
    }

    /** Stock depleted - no idempotency check (action is idempotent) */
    private fun handleStockDepletedEvents(envelopes: List<CloudEventEnvelope>) {
        if (envelopes.isEmpty()) return
        val productIds = productEventMapper.toStockDepletedProductIds(envelopes)
        productIds.forEach { productCacheRepository.evictProductCache(it) }
    }

    private fun isAlreadyHandled(aggregateType: String, aggregateId: String, action: String): Boolean =
        eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(aggregateType, aggregateId, action)

    private fun markAsHandled(aggregateType: String, aggregateId: String, action: String) {
        eventHandledRepository.save(EventHandled.create(aggregateType, aggregateId, action))
    }

    companion object {
        private val LIKE_EVENT_TYPES = setOf("loopers.like.created.v1", "loopers.like.canceled.v1")
    }
}
