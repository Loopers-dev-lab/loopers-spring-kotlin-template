package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.product.ProductStatisticService
import com.loopers.eventschema.CloudEventEnvelope
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
 * - 멱등성: 불필요 (도메인 자체 멱등)
 * - 에러 처리: BatchListenerFailedException으로 DLT 전송
 */
@Component
class ProductLikeEventConsumer(
    private val productStatisticService: ProductStatisticService,
    private val productEventMapper: ProductEventMapper,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val SUPPORTED_TYPES = setOf(
            "loopers.like.created.v1",
            "loopers.like.canceled.v1",
        )
    }

    @KafkaListener(topics = ["like-events"], containerFactory = KafkaConfig.BATCH_LISTENER)
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
            val command = productEventMapper.toLikeCommand(targetEnvelopes)
            productStatisticService.updateLikeCount(command)
        }

        ack.acknowledge()
    }
}
