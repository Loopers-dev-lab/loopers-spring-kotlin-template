package com.loopers.domain.ranking

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
) {
    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
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
                statHour = item.statHour.truncatedTo(ChronoUnit.HOURS),
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
     * 2. RankingScoreCalculator.calculateForHourly로 감쇠 공식 적용한 점수 계산
     * 3. Redis에 replaceAll로 업데이트
     *
     * 이전 버킷에만 있는 상품도 포함 (cold start prevention)
     *
     * @param dateTime 버킷 기준 시간
     */
    fun calculateHourRankings(dateTime: ZonedDateTime = ZonedDateTime.now(SEOUL_ZONE)) {
        val currentHour = dateTime.toInstant().truncatedTo(ChronoUnit.HOURS)
        val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

        // DB에서 현재/이전 버킷 조회
        val currentMetrics = metricRepository.findAllByStatHour(currentHour)
        val previousMetrics = metricRepository.findAllByStatHour(previousHour)

        if (currentMetrics.isEmpty() && previousMetrics.isEmpty()) {
            logger.debug("No metrics found for score calculation")
            return
        }

        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        // Calculator에 위임하여 감쇠 공식 적용된 점수 계산
        val finalScores = scoreCalculator.calculateForHourly(currentMetrics, previousMetrics, weights)

        // Redis에 업데이트
        rankingWriter.replaceAll(RankingPeriod.HOURLY, currentHour, finalScores)

        logger.info(
            "Calculated and updated scores for {} products (current: {}, previous: {})",
            finalScores.size,
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
    fun rollupHourlyToDaily(date: LocalDate = LocalDate.now(SEOUL_ZONE)) {
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
     * 1. 대상 날짜와 전날의 일별 메트릭을 조회
     * 2. RankingScoreCalculator.calculateForDaily로 감쇠 공식 적용하여 점수 계산
     * 3. Redis에 일별 버킷으로 저장
     *
     * 감쇠 공식: previousScore * 0.1 + currentScore * 0.9
     *
     * @param date 랭킹 계산 대상 날짜 (기본값: 오늘)
     */
    fun calculateDailyRankings(date: LocalDate = LocalDate.now(SEOUL_ZONE)) {
        val currentDailyMetrics = dailyMetricRepository.findAllByStatDate(date)
        val previousDailyMetrics = dailyMetricRepository.findAllByStatDate(date.minusDays(1))

        if (currentDailyMetrics.isEmpty() && previousDailyMetrics.isEmpty()) {
            logger.debug("No daily metrics found for date: {}", date)
            return
        }

        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        // Calculator에 위임하여 감쇠 공식 적용된 점수 계산
        val scores = scoreCalculator.calculateForDaily(currentDailyMetrics, previousDailyMetrics, weights)

        // Redis에 일별 버킷으로 저장
        val dateTimeInstant = date.atStartOfDay(SEOUL_ZONE).toInstant()
        rankingWriter.replaceAll(RankingPeriod.DAILY, dateTimeInstant, scores)

        logger.info(
            "Calculated and updated daily rankings for {} products on date: {}",
            scores.size,
            date,
        )
    }
}
