package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.ProductStatisticService
import com.loopers.domain.product.UpdateSalesCountCommand
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.idempotency.EventHandledService
import org.apache.kafka.clients.consumer.ConsumerRecord
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
 * - 순차 처리: 레코드별 처리로 멱등성 보장
 */
@Component
class ProductOrderEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productEventMapper: ProductEventMapper,
    private val eventHandledService: EventHandledService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONSUMER_GROUP = "product-statistic"
        val SUPPORTED_TYPES = setOf("loopers.order.paid.v1")
    }

    private val idempotencyKeyExtractor: (CloudEventEnvelope) -> String = { envelope ->
        val eventType = envelope.type.split(".")[2]
        "$CONSUMER_GROUP:${envelope.aggregateType}:${envelope.aggregateId}:$eventType"
    }

    @KafkaListener(topics = ["order-events"], containerFactory = KafkaConfig.BATCH_LISTENER, groupId = "product-statistic")
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        for (record in messages) {
            try {
                processRecord(record)
            } catch (e: Exception) {
                throw BatchListenerFailedException("Failed to process record", e, record)
            }
        }
        ack.acknowledge()
    }

    private fun processRecord(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
        if (envelope.type !in SUPPORTED_TYPES) return
        val idempotencyKey = idempotencyKeyExtractor(envelope)
        if (eventHandledService.isAlreadyHandled(idempotencyKey)) return
        val items = productEventMapper.toSalesItems(envelope)
        productStatisticService.updateSalesCount(UpdateSalesCountCommand(items))
        eventHandledService.markAsHandled(idempotencyKey)
    }
}
