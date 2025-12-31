package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * RankingWeightRecalculationService - 가중치 변경 시 점수 재계산 서비스
 *
 * - 가중치 변경 이벤트 발생 시 현재/다음 버킷의 점수를 재계산
 * - 현재 버킷: (현재 metric x weight) + (이전 metric x weight x 0.1)
 * - 다음 버킷: 스케줄러가 이미 생성한 경우에만 업데이트 (현재 버킷 점수 x 0.1)
 * - 이전 버킷: 읽기만 수행 (저장 X)
 */
@Service
class RankingWeightRecalculationService(
    private val metricJpaRepository: ProductHourlyMetricJpaRepository,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
    private val rankingWriter: ProductRankingWriter,
    private val rankingReader: ProductRankingReader,
    private val eventHandledRepository: EventHandledRepository,
) {
    companion object {
        private val DECAY_FACTOR = BigDecimal("0.1")
        private const val MAX_RETRY_COUNT = 3
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
        private const val IDEMPOTENCY_PREFIX = "ranking-weight-recalculation"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 점수 재계산 메인 진입점
     *
     * 1. 멱등성 체크
     * 2. 현재 버킷 재계산 (이전 버킷 감쇠 포함)
     * 3. 다음 버킷 업데이트 (존재하는 경우에만)
     * 4. 멱등성 키 저장
     */
    fun recalculateScores(command: RecalculateScoresCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) {
            logger.debug("Event already handled, skipping: {}", command.eventId)
            return
        }

        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        recalculateCurrentBucket(weights)
        updateNextBucketIfExists()

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
        logger.info(
            "Score recalculation completed with new weights: view={}, like={}, order={}",
            weights.viewWeight,
            weights.likeWeight,
            weights.orderWeight,
        )
    }

    /**
     * 현재 버킷 재계산
     *
     * = (현재 metric x weight) + (이전 metric x weight x 0.1)
     *
     * 이전 버킷에만 있는 상품도 감쇠 적용하여 포함 (cold start 방지)
     */
    private fun recalculateCurrentBucket(weights: RankingWeight) {
        val now = Instant.now()
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

        val currentStatHour = ZonedDateTime.ofInstant(currentHour, ZONE_ID)
        val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZONE_ID)

        val currentMetrics = metricJpaRepository.findAllByStatHour(currentStatHour)
        val previousMetrics = metricJpaRepository.findAllByStatHour(previousStatHour)

        // 이전 점수 계산 (저장 X, decay 계산용)
        val previousScores: Map<Long, Score> = previousMetrics.associate { metric ->
            metric.productId to scoreCalculator.calculate(metric.toSnapshot(), weights)
        }

        // 현재 점수 계산 + decay 적용
        val currentScores: MutableMap<Long, Score> = currentMetrics.associate { metric ->
            val baseScore = scoreCalculator.calculate(metric.toSnapshot(), weights)
            val decayScore = previousScores[metric.productId]?.applyDecay(DECAY_FACTOR) ?: Score.ZERO
            metric.productId to baseScore.plus(decayScore)
        }.toMutableMap()

        // 이전 버킷에만 있는 상품도 decay 적용하여 포함 (cold start 방지)
        previousScores.forEach { (productId, score) ->
            if (productId !in currentScores) {
                currentScores[productId] = score.applyDecay(DECAY_FACTOR)
            }
        }

        if (currentScores.isEmpty()) {
            logger.debug("No metrics found for recalculation")
            return
        }

        executeWithRetry("Current bucket recalculation") {
            rankingWriter.replaceAll(RankingKeyGenerator.currentBucketKey(), currentScores)
        }

        logger.info("Recalculated {} product scores for current bucket", currentScores.size)
    }

    /**
     * 다음 버킷 업데이트 (스케줄러가 이미 만들어놨으면)
     *
     * = 현재 버킷 점수 x 0.1
     */
    private fun updateNextBucketIfExists() {
        val nextBucketKey = RankingKeyGenerator.nextBucketKey()

        // 다음 버킷이 존재하는지 확인
        if (!rankingReader.exists(nextBucketKey)) {
            logger.debug("Next bucket does not exist, skipping update: {}", nextBucketKey)
            return
        }

        // 현재 버킷의 점수를 가져와서 decay 적용
        val currentBucketKey = RankingKeyGenerator.currentBucketKey()
        val currentScores = rankingReader.getAllScores(currentBucketKey)

        if (currentScores.isEmpty()) {
            logger.debug("Current bucket is empty, skipping next bucket update")
            return
        }

        val nextScores = currentScores.mapValues { (_, score) ->
            score.applyDecay(DECAY_FACTOR)
        }

        executeWithRetry("Next bucket update") {
            rankingWriter.replaceAll(nextBucketKey, nextScores)
        }

        logger.info("Updated {} product scores for next bucket with decay", nextScores.size)
    }

    /**
     * 재시도 로직 래퍼
     *
     * 실패 시 최대 3회까지 재시도하고, 모든 시도가 실패하면 예외를 던짐
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

        logger.error("{} failed after {} retry attempts", operationName, MAX_RETRY_COUNT)
        throw lastException!!
    }
}
