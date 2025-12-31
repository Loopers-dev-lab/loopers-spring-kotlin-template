package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.ranking.AccumulateMetricsCommand
import com.loopers.domain.ranking.RankingAggregationService
import com.loopers.eventschema.CloudEventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * RankingEventConsumer - 랭킹 집계를 위한 Kafka Consumer
 *
 * - 구독 토픽: product-events, like-events, order-events
 * - 지원 이벤트: loopers.product.viewed.v1, loopers.like.created.v1, loopers.like.canceled.v1, loopers.order.paid.v1
 * - 처리 방식: 배치 처리 (batch processing)
 * - 멱등성: DB upsert로 자동 처리
 */
@Component
class RankingEventConsumer(
    private val rankingAggregationService: RankingAggregationService,
    private val rankingEventMapper: RankingEventMapper,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["product-events", "like-events", "order-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
        groupId = "ranking-aggregation",
    )
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        val items = messages.flatMap { record ->
            mapToCommandItems(record)
        }

        if (items.isNotEmpty()) {
            rankingAggregationService.accumulateMetrics(AccumulateMetricsCommand(items))
            logger.debug("Processed {} ranking events, {} items", messages.size, items.size)
        }

        ack.acknowledge()
    }

    private fun mapToCommandItems(record: ConsumerRecord<String, String>): List<AccumulateMetricsCommand.Item> {
        return try {
            val envelope = objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
            rankingEventMapper.toCommandItems(envelope)
        } catch (e: Exception) {
            logger.warn("Failed to parse record: {}", e.message)
            emptyList()
        }
    }
}
