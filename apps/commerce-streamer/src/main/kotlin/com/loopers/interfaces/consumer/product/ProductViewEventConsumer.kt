package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.ProductStatisticService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.idempotency.EventHandledService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * ProductViewEventConsumer - product-events topic consumer for Kafka
 *
 * - Topic: product-events
 * - Supported events: loopers.product.viewed.v1
 * - Idempotency: DB-based (idempotencyKey: {consumerGroup}:{eventId})
 * - Error handling: BatchListenerFailedException for DLT routing
 */
@Component
class ProductViewEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productEventMapper: ProductEventMapper,
    private val eventHandledService: EventHandledService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONSUMER_GROUP = "product-statistic"
    }

    private val idempotencyKeyStrategies: Map<String, (CloudEventEnvelope) -> String> = mapOf(
        "loopers.product.viewed.v1" to { envelope ->
            "$CONSUMER_GROUP:${envelope.id}"
        },
    )

    private val supportedTypes = idempotencyKeyStrategies.keys

    @KafkaListener(topics = ["product-events"], containerFactory = KafkaConfig.BATCH_LISTENER)
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        // 1. Parse + Filter
        val parsedEnvelopes = mutableListOf<Pair<CloudEventEnvelope, String>>()

        for (record in messages) {
            val envelope = try {
                objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
            } catch (e: Exception) {
                throw BatchListenerFailedException("Failed to parse", e, record)
            }

            if (envelope.type !in supportedTypes) continue

            val idempotencyKey = idempotencyKeyStrategies[envelope.type]!!.invoke(envelope)
            parsedEnvelopes.add(envelope to idempotencyKey)
        }

        if (parsedEnvelopes.isEmpty()) {
            ack.acknowledge()
            return
        }

        // 2. Check DB idempotency (batch)
        val allKeys = parsedEnvelopes.map { (_, key) -> key }.toSet()
        val existingKeys = eventHandledService.findAllExistingKeys(allKeys)
        val newEnvelopes = parsedEnvelopes.filter { (_, key) -> key !in existingKeys }

        if (newEnvelopes.isEmpty()) {
            ack.acknowledge()
            return
        }

        // 3. Process business logic
        val envelopesToProcess = newEnvelopes.map { (envelope, _) -> envelope }
        val command = productEventMapper.toViewCommand(envelopesToProcess)
        productStatisticService.updateViewCount(command)

        // 4. Save idempotency keys (batch)
        eventHandledService.markAllAsHandled(newEnvelopes.map { (_, key) -> key })

        ack.acknowledge()
    }
}
