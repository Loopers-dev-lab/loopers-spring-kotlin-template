package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.ranking.CountSnapshot
import com.loopers.domain.ranking.ProductRankingWriter
import com.loopers.domain.ranking.RankingKeyGenerator
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingWeight
import com.loopers.domain.ranking.RankingWeightRepository
import com.loopers.domain.ranking.Score
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.support.idempotency.EventHandledService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * RankingWeightChangedEventConsumer - 가중치 변경 이벤트를 소비하는 Kafka Consumer
 *
 * - 구독 토픽: ranking-events
 * - 지원 이벤트: loopers.ranking.weight-changed.v1
 * - 멱등성: DB 기반 (idempotencyKey: {consumerGroup}:{eventId})
 * - 처리 방식: 전체 점수 재계산 (현재 시간대 버킷의 모든 상품 점수를 새 가중치로 재계산)
 */
@Component
class RankingWeightChangedEventConsumer(
    private val metricJpaRepository: ProductHourlyMetricJpaRepository,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
    private val rankingWriter: ProductRankingWriter,
    private val eventHandledService: EventHandledService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONSUMER_GROUP = "ranking-weight-changed"
        private const val SUPPORTED_TYPE = "loopers.ranking.weight-changed.v1"
        private val TTL_SECONDS = Duration.ofHours(25).seconds
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val idempotencyKeyExtractor: (CloudEventEnvelope) -> String = { envelope ->
        "$CONSUMER_GROUP:${envelope.id}"
    }

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

        val idempotencyKey = idempotencyKeyExtractor(envelope)
        if (eventHandledService.isAlreadyHandled(idempotencyKey)) return

        recalculateAllScores()

        eventHandledService.markAsHandled(idempotencyKey)
        logger.info("Weight change event processed: {}", envelope.id)
    }

    /**
     * 현재 시간대 버킷의 모든 점수를 새 가중치로 재계산
     *
     * 1. ProductHourlyMetric에서 현재 시간대의 모든 상품 메트릭 조회
     * 2. 새 가중치로 각 상품의 점수 재계산
     * 3. Redis ZSET에 전체 교체 (replaceAll)
     */
    private fun recalculateAllScores() {
        val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val currentBucketKey = RankingKeyGenerator.currentBucketKey()
        val statHour = ZonedDateTime.ofInstant(currentHour, ZONE_ID)

        val metrics = metricJpaRepository.findAllByStatHour(statHour)
        if (metrics.isEmpty()) {
            logger.debug("No metrics found for current hour: {}", statHour)
            return
        }

        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        val newScores: Map<Long, Score> = metrics.associate { metric ->
            val snapshot = CountSnapshot(
                views = metric.viewCount,
                likes = metric.likeCount,
                orderCount = metric.orderCount,
                orderAmount = metric.orderAmount,
            )
            metric.productId to scoreCalculator.calculate(snapshot, weights)
        }

        rankingWriter.replaceAll(currentBucketKey, newScores, TTL_SECONDS)

        logger.info(
            "Recalculated {} product scores with new weights (view={}, like={}, order={})",
            newScores.size,
            weights.viewWeight,
            weights.likeWeight,
            weights.orderWeight,
        )
    }
}
