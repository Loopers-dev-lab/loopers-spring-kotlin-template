package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.EvictStockDepletedCommand
import com.loopers.domain.product.ProductCacheService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.idempotency.EventHandledService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * ProductStockEventConsumer - stock-events 토픽을 소비하는 Kafka Consumer
 *
 * - 구독 토픽: stock-events
 * - 지원 이벤트: loopers.stock.depleted.v1
 * - 멱등성: DB 기반 (idempotencyKey: {consumerGroup}:{eventId})
 * - 에러 처리: BatchListenerFailedException으로 DLT 전송
 */
@Component
class ProductStockEventConsumer(
    private val productCacheService: ProductCacheService,
    private val productEventMapper: ProductEventMapper,
    private val eventHandledService: EventHandledService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONSUMER_GROUP = "product-cache"
        private val SUPPORTED_TYPES = setOf("loopers.stock.depleted.v1")
    }

    private val idempotencyKeyExtractor: (CloudEventEnvelope) -> String = { envelope ->
        "$CONSUMER_GROUP:${envelope.id}"
    }

    @KafkaListener(topics = ["stock-events"], containerFactory = KafkaConfig.BATCH_LISTENER, groupId = "product-cache")
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        for ((index, record) in messages.withIndex()) {
            try {
                processRecord(record)
            } catch (e: Exception) {
                throw BatchListenerFailedException("Failed to process", e, index)
            }
        }
        ack.acknowledge()
    }

    private fun processRecord(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
        if (envelope.type !in SUPPORTED_TYPES) return
        val idempotencyKey = idempotencyKeyExtractor(envelope)
        if (eventHandledService.isAlreadyHandled(idempotencyKey)) return
        val productId = productEventMapper.toStockDepletedProductId(envelope)
        productCacheService.evictStockDepletedProducts(EvictStockDepletedCommand(listOf(productId)))
        eventHandledService.markAsHandled(idempotencyKey)
    }
}
