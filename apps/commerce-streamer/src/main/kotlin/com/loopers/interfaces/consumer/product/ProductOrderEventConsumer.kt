package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.ProductStatisticService
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
 * ProductOrderEventConsumer - order-events 토픽을 소비하는 Kafka Consumer
 *
 * - 구독 토픽: order-events
 * - 지원 이벤트: loopers.order.paid.v1
 * - 멱등성: DB 기반 (idempotencyKey: {consumerGroup}:{aggregateType}:{aggregateId}:{eventType})
 * - 에러 처리: BatchListenerFailedException으로 DLT 전송
 * - 배치 내 중복 제거: 같은 aggregateId에 대해 최신 시간의 이벤트만 처리
 */
@Component
class ProductOrderEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productEventMapper: ProductEventMapper,
    private val eventHandledRepository: EventHandledRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CONSUMER_GROUP = "product-statistic"
    }

    private val idempotencyKeyStrategies: Map<String, (CloudEventEnvelope) -> String> = mapOf(
        "loopers.order.paid.v1" to { envelope ->
            val eventType = envelope.type.split(".")[2]
            "$CONSUMER_GROUP:${envelope.aggregateType}:${envelope.aggregateId}:$eventType"
        },
    )

    private val supportedTypes = idempotencyKeyStrategies.keys

    @KafkaListener(topics = ["order-events"], containerFactory = KafkaConfig.BATCH_LISTENER)
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

        // 2. Deduplicate within batch (keep latest by time)
        val deduplicatedEnvelopes = parsedEnvelopes
            .groupBy { (envelope, _) -> envelope.aggregateId }
            .map { (_, envelopes) ->
                envelopes.maxBy { (envelope, _) -> envelope.time }
            }

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
        val command = productEventMapper.toSalesCommand(envelopesToProcess)
        productStatisticService.updateSalesCount(command)

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
