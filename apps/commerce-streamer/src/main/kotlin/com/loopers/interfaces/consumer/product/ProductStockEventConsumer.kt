package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.EvictStockDepletedCommand
import com.loopers.domain.product.ProductCacheService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
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
    private val eventHandledRepository: EventHandledRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CONSUMER_GROUP = "product-cache"
        private val SUPPORTED_TYPES = setOf("loopers.stock.depleted.v1")
    }

    @KafkaListener(topics = ["stock-events"], containerFactory = KafkaConfig.BATCH_LISTENER)
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        // 1. Parse + Filter
        val parsedEnvelopes = mutableListOf<Pair<CloudEventEnvelope, String>>()

        for (record in messages) {
            val envelope = try {
                objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
            } catch (e: Exception) {
                throw BatchListenerFailedException("Failed to parse", e, record)
            }

            if (envelope.type !in SUPPORTED_TYPES) continue

            val idempotencyKey = "$CONSUMER_GROUP:${envelope.id}"
            parsedEnvelopes.add(envelope to idempotencyKey)
        }

        if (parsedEnvelopes.isEmpty()) {
            ack.acknowledge()
            return
        }

        // 2. Deduplicate within batch by event ID
        val deduplicatedEnvelopes = parsedEnvelopes.distinctBy { (envelope, _) -> envelope.id }

        // 3. Check DB idempotency (batch)
        val allKeys = deduplicatedEnvelopes.map { (_, key) -> key }.toSet()
        val existingKeys = eventHandledRepository.findAllExistingKeys(allKeys)
        val newEnvelopes = deduplicatedEnvelopes.filter { (_, key) -> key !in existingKeys }

        if (newEnvelopes.isEmpty()) {
            ack.acknowledge()
            return
        }

        // 4. Process business logic
        val envelopesToProcess = newEnvelopes.map { (envelope, _) -> envelope }
        val productIds = productEventMapper.toStockDepletedProductIds(envelopesToProcess)
        productCacheService.evictStockDepletedProducts(EvictStockDepletedCommand(productIds))

        // 5. Save idempotency keys (batch, ignore errors)
        runCatching {
            eventHandledRepository.saveAll(
                newEnvelopes.map { (_, key) -> EventHandled(idempotencyKey = key) },
            )
        }.onFailure { e ->
            log.warn("Failed to save idempotency keys, duplicates may occur on retry", e)
        }

        ack.acknowledge()
    }
}
