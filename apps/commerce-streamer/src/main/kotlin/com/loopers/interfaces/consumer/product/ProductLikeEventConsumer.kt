package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.ProductStatisticService
import com.loopers.domain.product.UpdateLikeCountCommand
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.idempotency.EventHandledService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * ProductLikeEventConsumer - like-events 토픽을 소비하는 Kafka Consumer
 *
 * - 구독 토픽: like-events
 * - 지원 이벤트: loopers.like.created.v1, loopers.like.canceled.v1
 * - 멱등성: DB 기반 (idempotencyKey: {consumerGroup}:{eventId})
 * - 처리 방식: 레코드 단위 순차 처리
 */
@Component
class ProductLikeEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productEventMapper: ProductEventMapper,
    private val eventHandledService: EventHandledService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONSUMER_GROUP = "product-statistic"
        private val SUPPORTED_TYPES = setOf(
            "loopers.like.created.v1",
            "loopers.like.canceled.v1",
        )
    }

    private val idempotencyKeyExtractor: (CloudEventEnvelope) -> String = { envelope ->
        "$CONSUMER_GROUP:${envelope.id}"
    }

    @KafkaListener(topics = ["like-events"], containerFactory = KafkaConfig.BATCH_LISTENER)
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        for (record in messages) {
            processRecord(record)
        }
        ack.acknowledge()
    }

    private fun processRecord(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
        if (envelope.type !in SUPPORTED_TYPES) return
        val idempotencyKey = idempotencyKeyExtractor(envelope)
        if (eventHandledService.isAlreadyHandled(idempotencyKey)) return
        val item = productEventMapper.toLikeItem(envelope)
        productStatisticService.updateLikeCount(UpdateLikeCountCommand(listOf(item)))
        eventHandledService.markAsHandled(idempotencyKey)
    }
}
