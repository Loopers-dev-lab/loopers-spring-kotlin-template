package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.ProductStatisticService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * ProductLikeEventConsumer - like-events 토픽을 소비하는 Kafka Consumer
 *
 * - 구독 토픽: like-events
 * - 지원 이벤트: loopers.like.created.v1, loopers.like.canceled.v1
 * - 멱등성: DB 기반 (idempotencyKey: {consumerGroup}:{eventId})
 * - 에러 처리: BatchListenerFailedException으로 DLT 전송
 * - 배치 내 중복 제거: 동일 eventId에 대해 하나만 처리
 */
@Component
class ProductLikeEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productEventMapper: ProductEventMapper,
    private val eventHandledRepository: EventHandledRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONSUMER_GROUP = "product-statistic"
    }

    private val supportedTypes = setOf(
        "loopers.like.created.v1",
        "loopers.like.canceled.v1",
    )

    @KafkaListener(topics = ["like-events"], containerFactory = KafkaConfig.BATCH_LISTENER)
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

            val idempotencyKey = "$CONSUMER_GROUP:${envelope.id}"
            parsedEnvelopes.add(envelope to idempotencyKey)
        }

        if (parsedEnvelopes.isEmpty()) {
            ack.acknowledge()
            return
        }

        // 2. Deduplicate within batch by event ID
        val deduplicatedEnvelopes = parsedEnvelopes
            .distinctBy { (envelope, _) -> envelope.id }

        // 3. Check DB idempotency
        val newEnvelopes = deduplicatedEnvelopes.filter { (_, idempotencyKey) ->
            !eventHandledRepository.existsByIdempotencyKey(idempotencyKey)
        }

        if (newEnvelopes.isEmpty()) {
            ack.acknowledge()
            return
        }

        // 4. Process business logic
        val envelopesToProcess = newEnvelopes.map { (envelope, _) -> envelope }
        val command = productEventMapper.toLikeCommand(envelopesToProcess)
        productStatisticService.updateLikeCount(command)

        // 5. Save idempotency keys
        newEnvelopes.forEach { (_, idempotencyKey) ->
            eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
        }

        ack.acknowledge()
    }
}
