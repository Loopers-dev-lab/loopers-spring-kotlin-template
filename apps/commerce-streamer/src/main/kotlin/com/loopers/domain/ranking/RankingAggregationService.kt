package com.loopers.domain.ranking

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * RankingAggregationService - 랭킹 집계 핵심 서비스
 *
 * - accumulateMetrics: 배치로 메트릭을 DB에 저장
 * - calculateAndUpdateScores: DB에서 조회 후 감쇠 공식 적용하여 Redis 업데이트
 */
@Service
class RankingAggregationService(
    private val metricRepository: ProductHourlyMetricRepository,
    private val rankingWriter: ProductRankingWriter,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
) {
    companion object {
        private val DECAY_FACTOR = java.math.BigDecimal("0.1")
        private val CURRENT_WEIGHT = java.math.BigDecimal("0.9")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 배치로 메트릭을 DB에 직접 저장
     *
     * - Consumer에서 배치로 호출
     * - ProductHourlyMetricRow로 변환하여 batchAccumulateCounts 호출
     *
     * @param command 배치 메트릭 커맨드
     */
    fun accumulateMetrics(command: AccumulateMetricsCommand) {
        if (command.items.isEmpty()) return

        val rows = command.items.map { item ->
            ProductHourlyMetricRow(
                productId = item.productId,
                statHour = item.statHour.toInstant().truncatedTo(ChronoUnit.HOURS),
                viewCount = item.viewDelta,
                likeCount = item.likeCreatedDelta - item.likeCanceledDelta,
                orderCount = item.orderCountDelta,
                orderAmount = item.orderAmountDelta,
            )
        }

        metricRepository.batchAccumulateCounts(rows)
        logger.debug("Accumulated {} metrics to DB", rows.size)
    }

    /**
     * 점수 계산 및 Redis 업데이트
     *
     * 1. 현재 시간과 이전 시간 버킷의 메트릭을 DB에서 조회
     * 2. 각 상품별 기본 점수 계산 (current bucket)
     * 3. 감쇠 공식 적용: previousScore * 0.1 + currentScore * 0.9
     * 4. Redis에 replaceAll로 업데이트
     *
     * 이전 버킷에만 있는 상품도 포함 (cold start prevention)
     */
    fun calculateAndUpdateScores() {
        val now = Instant.now()
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

        // DB에서 현재/이전 버킷 조회
        val currentMetrics = metricRepository.findAllByStatHour(currentHour)
        val previousMetrics = metricRepository.findAllByStatHour(previousHour)

        if (currentMetrics.isEmpty() && previousMetrics.isEmpty()) {
            logger.debug("No metrics found for score calculation")
            return
        }

        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        // 현재 버킷 기준 점수 계산 (productId -> Score)
        val currentScores = currentMetrics.associate { metric ->
            val snapshot = metric.toSnapshot()
            val score = scoreCalculator.calculate(snapshot, weights)
            metric.productId to score
        }

        // 이전 버킷 기준 점수 계산 (productId -> Score)
        val previousScores = previousMetrics.associate { metric ->
            val snapshot = metric.toSnapshot()
            val score = scoreCalculator.calculate(snapshot, weights)
            metric.productId to score
        }

        // 모든 상품 ID 수집 (현재 + 이전)
        val allProductIds = (currentScores.keys + previousScores.keys).toSet()

        // 감쇠 공식 적용: previousScore * 0.1 + currentScore * 0.9
        val finalScores = allProductIds.associateWith { productId ->
            val currentScore = currentScores[productId] ?: Score.ZERO
            val previousScore = previousScores[productId] ?: Score.ZERO

            // decay formula: previous * 0.1 + current * 0.9
            val decayedPrevious = previousScore.applyDecay(DECAY_FACTOR)
            val weightedCurrent = currentScore.applyDecay(CURRENT_WEIGHT)
            decayedPrevious + weightedCurrent
        }

        // Redis에 업데이트
        val bucketKey = RankingKeyGenerator.currentBucketKey()
        rankingWriter.replaceAll(bucketKey, finalScores)

        logger.info(
            "Calculated and updated scores for {} products (current: {}, previous: {})",
            allProductIds.size,
            currentMetrics.size,
            previousMetrics.size,
        )
    }
}
