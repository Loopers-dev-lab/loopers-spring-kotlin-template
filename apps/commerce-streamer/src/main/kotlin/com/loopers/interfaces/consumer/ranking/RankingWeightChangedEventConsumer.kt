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
import java.math.BigDecimal
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
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
        private val DECAY_FACTOR = BigDecimal("0.1")
        private const val MAX_RETRY_COUNT = 3
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

        recalculateBuckets()

        eventHandledService.markAsHandled(idempotencyKey)
        logger.info("Weight change event processed: {}", envelope.id)
    }

    /**
     * 현재 및 이전 시간대 버킷의 모든 점수를 새 가중치로 재계산
     *
     * 1. 현재 버킷 재계산 (retry 포함)
     * 2. 이전 버킷 재계산 (decay 적용, retry 포함)
     */
    private fun recalculateBuckets() {
        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        recalculateCurrentBucket(weights)
        recalculatePreviousBucket(weights)
    }

    /**
     * 현재 시간대 버킷의 모든 점수를 새 가중치로 재계산
     *
     * 1. ProductHourlyMetric에서 현재 시간대의 모든 상품 메트릭 조회
     * 2. 새 가중치로 각 상품의 점수 재계산
     * 3. Redis ZSET에 전체 교체 (replaceAll)
     */
    private fun recalculateCurrentBucket(weights: RankingWeight) {
        val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val currentBucketKey = RankingKeyGenerator.currentBucketKey()
        val statHour = ZonedDateTime.ofInstant(currentHour, ZONE_ID)

        val metrics = metricJpaRepository.findAllByStatHour(statHour)
        if (metrics.isEmpty()) {
            logger.debug("No metrics found for current hour: {}", statHour)
            return
        }

        val newScores: Map<Long, Score> = metrics.associate { metric ->
            val snapshot = CountSnapshot(
                views = metric.viewCount,
                likes = metric.likeCount,
                orderCount = metric.orderCount,
                orderAmount = metric.orderAmount,
            )
            metric.productId to scoreCalculator.calculate(snapshot, weights)
        }

        executeWithRetry("Current bucket recalculation") {
            rankingWriter.replaceAll(currentBucketKey, newScores)
        }

        logger.info(
            "Recalculated {} product scores for current bucket with new weights (view={}, like={}, order={})",
            newScores.size,
            weights.viewWeight,
            weights.likeWeight,
            weights.orderWeight,
        )
    }

    /**
     * 이전 시간대 버킷의 모든 점수를 새 가중치로 재계산하고 감쇠 적용
     *
     * 1. ProductHourlyMetric에서 이전 시간대의 모든 상품 메트릭 조회
     * 2. 새 가중치로 각 상품의 점수 재계산
     * 3. 감쇠 계수(0.1) 적용
     * 4. Redis ZSET에 전체 교체 (replaceAll)
     */
    private fun recalculatePreviousBucket(weights: RankingWeight) {
        val previousHour = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS)
        val previousBucketKey = RankingKeyGenerator.previousBucketKey()
        val statHour = ZonedDateTime.ofInstant(previousHour, ZONE_ID)

        val metrics = metricJpaRepository.findAllByStatHour(statHour)
        if (metrics.isEmpty()) {
            logger.debug("No metrics found for previous hour: {}", statHour)
            return
        }

        val decayedScores: Map<Long, Score> = metrics.associate { metric ->
            val snapshot = CountSnapshot(
                views = metric.viewCount,
                likes = metric.likeCount,
                orderCount = metric.orderCount,
                orderAmount = metric.orderAmount,
            )
            val originalScore = scoreCalculator.calculate(snapshot, weights)
            metric.productId to originalScore.applyDecay(DECAY_FACTOR)
        }

        executeWithRetry("Previous bucket recalculation") {
            rankingWriter.replaceAll(previousBucketKey, decayedScores)
        }

        logger.info(
            "Recalculated {} product scores for previous bucket with decay (factor={})",
            decayedScores.size,
            DECAY_FACTOR,
        )
    }

    /**
     * 재시도 로직 래퍼
     *
     * 실패 시 최대 3회까지 재시도하고, 모든 시도가 실패하면 에러 로깅 후 예외를 던짐
     */
    private fun executeWithRetry(
        operationName: String,
        action: () -> Unit,
    ) {
        var lastException: Exception? = null

        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                action()
                return
            } catch (e: Exception) {
                lastException = e
                logger.warn(
                    "{} failed, retry attempt {} of {}",
                    operationName,
                    attempt + 1,
                    MAX_RETRY_COUNT,
                )
            }
        }

        logger.error("{} failed after {} retry attempts, data may be lost", operationName, MAX_RETRY_COUNT)
        throw lastException!!
    }
}
