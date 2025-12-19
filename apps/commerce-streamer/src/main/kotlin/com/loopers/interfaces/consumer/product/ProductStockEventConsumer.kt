package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.config.kafka.KafkaConfig
import com.loopers.eventschema.CloudEventEnvelope
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
 * - 멱등성: 불필요 (캐시 무효화는 멱등)
 * - 에러 처리: BatchListenerFailedException으로 DLT 전송
 */
@Component
class ProductStockEventConsumer(
    private val cacheTemplate: CacheTemplate,
    private val productEventMapper: ProductEventMapper,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val SUPPORTED_TYPES = setOf(
            "loopers.stock.depleted.v1",
        )
    }

    @KafkaListener(topics = ["stock-events"], containerFactory = KafkaConfig.BATCH_LISTENER)
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        val targetEnvelopes = mutableListOf<CloudEventEnvelope>()

        for (record in messages) {
            val envelope = try {
                objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
            } catch (e: Exception) {
                throw BatchListenerFailedException("Failed to parse", e, record)
            }

            if (envelope.type !in SUPPORTED_TYPES) continue

            targetEnvelopes.add(envelope)
        }

        if (targetEnvelopes.isNotEmpty()) {
            val productIds = productEventMapper.toStockDepletedProductIds(targetEnvelopes)
            val cacheKeys = productIds.map { ProductCacheKeys.ProductDetail(it) }
            cacheTemplate.evictAll(cacheKeys)
        }

        ack.acknowledge()
    }
}
