package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.ranking.RankingAggregationService
import com.loopers.eventschema.CloudEventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * RankingEventConsumer - 랭킹 집계를 위한 Kafka Consumer
 *
 * - 구독 토픽: product-events, like-events, order-events
 * - 지원 이벤트: loopers.product.viewed.v1, loopers.like.created.v1, loopers.like.canceled.v1, loopers.order.paid.v1
 * - 멱등성: Service 레이어에서 처리 (EventHandledRepository 사용)
 * - 처리 방식: 레코드 단위 순차 처리, 순수 위임 (pure delegation)
 */
@Component
class RankingEventConsumer(
    private val rankingAggregationService: RankingAggregationService,
    private val rankingEventMapper: RankingEventMapper,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val SUPPORTED_TYPES = setOf(
            "loopers.product.viewed.v1",
            "loopers.like.created.v1",
            "loopers.like.canceled.v1",
            "loopers.order.paid.v1",
        )
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

        when (envelope.type) {
            "loopers.product.viewed.v1" -> {
                val command = rankingEventMapper.toViewCommand(envelope)
                rankingAggregationService.accumulateViewMetric(command)
            }
            "loopers.like.created.v1" -> {
                val command = rankingEventMapper.toLikeCreatedCommand(envelope)
                rankingAggregationService.accumulateLikeCreatedMetric(command)
            }
            "loopers.like.canceled.v1" -> {
                val command = rankingEventMapper.toLikeCanceledCommand(envelope)
                rankingAggregationService.accumulateLikeCanceledMetric(command)
            }
            "loopers.order.paid.v1" -> {
                val command = rankingEventMapper.toOrderPaidCommand(envelope)
                rankingAggregationService.accumulateOrderPaidMetric(command)
            }
        }
    }
}
