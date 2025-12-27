package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.ranking.RankingWeightRecalculationService
import com.loopers.domain.ranking.RecalculateScoresCommand
import com.loopers.eventschema.CloudEventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * RankingWeightChangedEventConsumer - 가중치 변경 이벤트를 소비하는 Kafka Consumer
 *
 * - 구독 토픽: ranking-events
 * - 지원 이벤트: loopers.ranking.weight-changed.v1
 * - 멱등성: 서비스 레이어에서 처리 (RankingWeightRecalculationService)
 * - 처리 방식: 서비스에 위임하여 점수 재계산
 */
@Component
class RankingWeightChangedEventConsumer(
    private val rankingWeightRecalculationService: RankingWeightRecalculationService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val SUPPORTED_TYPE = "loopers.ranking.weight-changed.v1"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["ranking-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
        groupId = "ranking-weight-changed",
    )
    fun consume(messages: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        for (record in messages) {
            processRecord(record)
        }
        ack.acknowledge()
    }

    private fun processRecord(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), CloudEventEnvelope::class.java)
        if (envelope.type != SUPPORTED_TYPE) return

        val command = RecalculateScoresCommand(eventId = envelope.id)
        rankingWeightRecalculationService.recalculateScores(command)

        logger.info("Weight change event processed: {}", envelope.id)
    }
}
