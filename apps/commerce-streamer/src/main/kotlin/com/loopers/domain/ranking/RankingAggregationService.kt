package com.loopers.domain.ranking

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * RankingAggregationService - 랭킹 집계 핵심 서비스
 *
 * - accumulateMetrics: 배치로 메트릭을 DB에 저장
 * - calculateAndUpdateScores: DB에서 조회 후 감쇠 공식 적용하여 Redis 업데이트
 * - rollupHourlyToDaily: 시간별 메트릭을 일별 메트릭으로 롤업
 * - calculateDailyRankings: 일별 랭킹 계산 및 Redis 업데이트
 */
@Service
class RankingAggregationService(
    private val metricRepository: ProductHourlyMetricRepository,
    private val dailyMetricRepository: ProductDailyMetricRepository,
    private val rankingWriter: ProductRankingWriter,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
    private val rankingKeyGenerator: RankingKeyGenerator,
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
        val bucketKey = rankingKeyGenerator.currentBucketKey()
        rankingWriter.replaceAll(bucketKey, finalScores)

        logger.info(
            "Calculated and updated scores for {} products (current: {}, previous: {})",
            allProductIds.size,
            currentMetrics.size,
            previousMetrics.size,
        )
    }

    /**
     * 시간별 메트릭을 일별 메트릭으로 롤업
     *
     * 1. 대상 날짜의 모든 시간별 메트릭을 조회 (00:00 ~ 23:59)
     * 2. 상품별로 그룹화하여 viewCount, likeCount, orderAmount 합산
     * 3. ProductDailyMetric으로 upsert
     *
     * @param date 롤업 대상 날짜 (기본값: 오늘)
     */
    fun rollupHourlyToDaily(date: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))) {
        val hourlyMetrics = metricRepository.findAllByDate(date)

        if (hourlyMetrics.isEmpty()) {
            logger.debug("No hourly metrics found for date: {}", date)
            return
        }

        // 상품별로 그룹화하여 합산
        val dailyMetrics = hourlyMetrics
            .groupBy { it.productId }
            .map { (productId, metrics) ->
                ProductDailyMetric.create(
                    statDate = date,
                    productId = productId,
                    viewCount = metrics.sumOf { it.viewCount },
                    likeCount = metrics.sumOf { it.likeCount },
                    orderAmount = metrics.fold(java.math.BigDecimal.ZERO) { acc, m -> acc + m.orderAmount },
                )
            }

        dailyMetricRepository.upsertFromHourly(dailyMetrics)

        logger.info(
            "Rolled up {} hourly metrics into {} daily metrics for date: {}",
            hourlyMetrics.size,
            dailyMetrics.size,
            date,
        )
    }

    /**
     * 일별 랭킹 계산 및 Redis 업데이트
     *
     * 1. 대상 날짜의 모든 일별 메트릭을 조회
     * 2. RankingScoreCalculator로 점수 계산
     * 3. Redis에 일별 버킷으로 저장
     *
     * @param date 랭킹 계산 대상 날짜 (기본값: 오늘)
     */
    fun calculateDailyRankings(date: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))) {
        val dailyMetrics = dailyMetricRepository.findAllByStatDate(date)

        if (dailyMetrics.isEmpty()) {
            logger.debug("No daily metrics found for date: {}", date)
            return
        }

        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        // 점수 계산
        val scores = dailyMetrics.associate { metric ->
            val snapshot = metric.toSnapshot()
            val score = scoreCalculator.calculate(snapshot, weights)
            metric.productId to score
        }

        // Redis에 일별 버킷으로 저장
        val dailyBucketKey = rankingKeyGenerator.dailyBucketKey(date)
        rankingWriter.replaceAll(dailyBucketKey, scores)

        logger.info(
            "Calculated and updated daily rankings for {} products on date: {}",
            scores.size,
            date,
        )
    }
}
