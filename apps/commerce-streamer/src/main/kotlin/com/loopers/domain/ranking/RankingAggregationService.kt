package com.loopers.domain.ranking

import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * RankingAggregationService - 랭킹 집계 핵심 서비스
 *
 * - accumulateMetrics: 배치로 메트릭을 DB에 저장
 * - calculateAndUpdateScores: DB에서 조회 후 감쇠 공식 적용하여 Redis 업데이트
 * - 기존 accumulate 메서드들은 consumer 호환성을 위해 임시로 유지 (Milestone 8에서 삭제 예정)
 */
@Service
class RankingAggregationService(
    private val metricRepository: ProductHourlyMetricRepository,
    private val rankingWriter: ProductRankingWriter,
    private val rankingReader: ProductRankingReader,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
    private val eventHandledRepository: EventHandledRepository,
) {
    companion object {
        private val DECAY_FACTOR = java.math.BigDecimal("0.1")
        private val CURRENT_WEIGHT = java.math.BigDecimal("0.9")
        private const val IDEMPOTENCY_PREFIX = "ranking"
        private const val BUFFER_HARD_THRESHOLD = 10000
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    // 버퍼는 서비스 내부에서 소유 (기존 accumulate 메서드들에서 사용 - Milestone 8에서 삭제 예정)
    private val buffer = MetricBuffer()

    // ==================== New Batch API ====================

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

    // ==================== Legacy API (will be removed in Milestone 8) ====================

    /**
     * VIEW 이벤트 메트릭을 축적
     *
     * 멱등성 체크 -> 버퍼 축적 -> 멱등성 기록 순서로 수행
     * 기록 실패는 비즈니스 로직에 영향을 주지 않음
     */
    fun accumulateViewMetric(command: AccumulateViewMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:view:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        buffer.incrementView(AggregationKey.of(command.productId, command.occurredAt))
        checkBufferThreshold()

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    /**
     * LIKE_CREATED 이벤트 메트릭을 축적
     */
    fun accumulateLikeCreatedMetric(command: AccumulateLikeCreatedMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:like-created:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        buffer.incrementLikeCreated(AggregationKey.of(command.productId, command.occurredAt))
        checkBufferThreshold()

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    /**
     * LIKE_CANCELED 이벤트 메트릭을 축적
     */
    fun accumulateLikeCanceledMetric(command: AccumulateLikeCanceledMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:like-canceled:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        buffer.incrementLikeCanceled(AggregationKey.of(command.productId, command.occurredAt))
        checkBufferThreshold()

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    /**
     * ORDER_PAID 이벤트 메트릭을 축적
     *
     * 여러 주문 아이템을 각각의 상품별로 버퍼에 축적
     */
    fun accumulateOrderPaidMetric(command: AccumulateOrderPaidMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:order-paid:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        command.items.forEach { item ->
            buffer.incrementOrderPaid(AggregationKey.of(item.productId, command.occurredAt), item.orderAmount)
            checkBufferThreshold()
        }

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    // ==================== Buffer Management (Legacy - will be removed in Milestone 8) ====================

    /**
     * 버퍼 임계치 체크 - Legacy, 현재 사용되지 않음
     */
    private fun checkBufferThreshold() {
        val currentSize = buffer.size()
        if (currentSize >= BUFFER_HARD_THRESHOLD) {
            logger.warn("Buffer reached threshold ({}), but flush is disabled", currentSize)
        }
    }
}
