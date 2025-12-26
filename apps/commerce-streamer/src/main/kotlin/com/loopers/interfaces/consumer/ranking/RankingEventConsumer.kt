package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.ranking.AccumulateMetricCommand
import com.loopers.domain.ranking.RankingAggregationService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.idempotency.EventHandledService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * RankingEventConsumer - 랭킹 집계를 위한 Kafka Consumer
 *
 * - 구독 토픽: product-events, like-events, order-events
 * - 지원 이벤트: loopers.product.viewed.v1, loopers.like.created.v1, loopers.like.canceled.v1, loopers.order.paid.v1
 * - 멱등성: DB 기반 (idempotencyKey: {consumerGroup}:{eventId})
 * - 처리 방식: 레코드 단위 순차 처리
 */
@Component
class RankingEventConsumer(
    private val rankingAggregationService: RankingAggregationService,
    private val rankingEventMapper: RankingEventMapper,
    private val eventHandledService: EventHandledService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONSUMER_GROUP = "ranking-aggregation"
        private val SUPPORTED_TYPES = setOf(
            "loopers.product.viewed.v1",
            "loopers.like.created.v1",
            "loopers.like.canceled.v1",
            "loopers.order.paid.v1",
        )
    }

    private val idempotencyKeyExtractor: (CloudEventEnvelope) -> String = { envelope ->
        "$CONSUMER_GROUP:${envelope.id}"
    }

    @KafkaListener(
        topics = ["product-events", "like-events", "order-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
        groupId = "ranking-aggregation",
    )
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
        val items = rankingEventMapper.toAccumulateMetricItems(envelope)
        rankingAggregationService.accumulateMetric(AccumulateMetricCommand(items = items))
        eventHandledService.markAsHandled(idempotencyKey)
    }
}
