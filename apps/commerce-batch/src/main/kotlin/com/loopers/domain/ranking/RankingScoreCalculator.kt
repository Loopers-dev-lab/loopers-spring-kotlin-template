package com.loopers.domain.ranking

import com.loopers.batch.job.ranking.step.ScoreEntry
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * RankingScoreCalculator - 랭킹 점수 계산 도메인 서비스
 *
 * - 상태를 갖지 않는 순수 계산 로직
 * - Score 계산 공식: viewCount x viewWeight + likeCount x likeWeight + orderAmount x orderWeight
 */
@Component
class RankingScoreCalculator {

    companion object {
        private const val SCALE = 2
        private val ROUNDING_MODE = RoundingMode.HALF_UP
        private val DECAY_FACTOR = BigDecimal("0.1")
        private val CURRENT_WEIGHT = BigDecimal("0.9")
    }

    /**
     * 시간별 메트릭 리스트에서 감쇠 공식을 적용하여 상품별 점수 계산
     *
     * 감쇠 공식: previousScore * 0.1 + currentScore * 0.9
     *
     * @param currentMetrics 현재 시간 버킷의 메트릭 리스트
     * @param previousMetrics 이전 시간 버킷의 메트릭 리스트
     * @param weight 가중치 설정
     * @return 상품 ID -> Score 맵
     */
    fun calculateForHourly(
        currentMetrics: List<ProductHourlyMetric>,
        previousMetrics: List<ProductHourlyMetric>,
        weight: RankingWeight,
    ): Map<Long, Score> {
        val currentScores = currentMetrics.associate { metric ->
            metric.productId to calculateScoreForMetric(
                metric.viewCount,
                metric.likeCount,
                metric.orderAmount,
                weight,
            )
        }

        val previousScores = previousMetrics.associate { metric ->
            metric.productId to calculateScoreForMetric(
                metric.viewCount,
                metric.likeCount,
                metric.orderAmount,
                weight,
            )
        }

        return applyDecayFormula(currentScores, previousScores)
    }

    /**
     * 일별 메트릭 리스트에서 감쇠 공식을 적용하여 상품별 점수 계산
     *
     * 감쇠 공식: previousScore * 0.1 + currentScore * 0.9
     *
     * @param currentMetrics 현재 날짜의 메트릭 리스트
     * @param previousMetrics 이전 날짜의 메트릭 리스트
     * @param weight 가중치 설정
     * @return 상품 ID -> Score 맵
     */
    fun calculateForDaily(
        currentMetrics: List<ProductDailyMetric>,
        previousMetrics: List<ProductDailyMetric>,
        weight: RankingWeight,
    ): Map<Long, Score> {
        val currentScores = currentMetrics.associate { metric ->
            metric.productId to calculateScoreForMetric(
                metric.viewCount,
                metric.likeCount,
                metric.orderAmount,
                weight,
            )
        }

        val previousScores = previousMetrics.associate { metric ->
            metric.productId to calculateScoreForMetric(
                metric.viewCount,
                metric.likeCount,
                metric.orderAmount,
                weight,
            )
        }

        return applyDecayFormula(currentScores, previousScores)
    }

    /**
     * 단일 시간별 메트릭에 대해 감쇠 가중치를 적용하여 ScoreEntry 반환
     *
     * 감쇠 공식:
     * - currentHour와 같은 버킷: rawScore * 0.9
     * - currentHour와 다른 버킷 (이전 시간): rawScore * 0.1
     *
     * @param metric 시간별 메트릭
     * @param currentHour 현재 시간 버킷
     * @param weight 가중치 설정
     * @return ScoreEntry (productId, 감쇠 적용된 점수)
     */
    fun calculateSingleHourly(
        metric: ProductHourlyMetric,
        currentHour: Instant,
        weight: RankingWeight,
    ): ScoreEntry {
        val rawScore = calculateScoreForMetric(
            metric.viewCount,
            metric.likeCount,
            metric.orderAmount,
            weight,
        )

        val decayWeight = if (metric.statHour == currentHour) CURRENT_WEIGHT else DECAY_FACTOR
        val decayedScore = rawScore.applyDecay(decayWeight)

        return ScoreEntry(
            productId = metric.productId,
            score = decayedScore.value,
        )
    }

    /**
     * 단일 일별 메트릭에 대해 감쇠 가중치를 적용하여 ScoreEntry 반환
     *
     * 감쇠 공식:
     * - baseDate와 같은 날짜: rawScore * 0.9
     * - baseDate와 다른 날짜 (이전 날짜): rawScore * 0.1
     *
     * @param metric 일별 메트릭
     * @param baseDate 기준 날짜
     * @param weight 가중치 설정
     * @return ScoreEntry (productId, 감쇠 적용된 점수)
     */
    fun calculateSingleDaily(
        metric: ProductDailyMetric,
        baseDate: LocalDate,
        weight: RankingWeight,
    ): ScoreEntry {
        val rawScore = calculateScoreForMetric(
            metric.viewCount,
            metric.likeCount,
            metric.orderAmount,
            weight,
        )

        val decayWeight = if (metric.statDate == baseDate) CURRENT_WEIGHT else DECAY_FACTOR
        val decayedScore = rawScore.applyDecay(decayWeight)

        return ScoreEntry(
            productId = metric.productId,
            score = decayedScore.value,
        )
    }

    /**
     * 메트릭 필드에서 직접 Score 계산
     */
    private fun calculateScoreForMetric(
        viewCount: Long,
        likeCount: Long,
        orderAmount: BigDecimal,
        weight: RankingWeight,
    ): Score {
        val viewScore = BigDecimal.valueOf(viewCount)
            .multiply(weight.viewWeight)

        val likeScore = BigDecimal.valueOf(likeCount)
            .multiply(weight.likeWeight)

        val orderScore = orderAmount
            .multiply(weight.orderWeight)

        val totalScore = viewScore
            .add(likeScore)
            .add(orderScore)
            .setScale(SCALE, ROUNDING_MODE)

        return Score.of(maxOf(totalScore, BigDecimal.ZERO))
    }

    /**
     * 감쇠 공식 적용: previousScore * 0.1 + currentScore * 0.9
     */
    private fun applyDecayFormula(
        currentScores: Map<Long, Score>,
        previousScores: Map<Long, Score>,
    ): Map<Long, Score> {
        val allProductIds = (currentScores.keys + previousScores.keys).toSet()

        return allProductIds.associateWith { productId ->
            val currentScore = currentScores[productId] ?: Score.ZERO
            val previousScore = previousScores[productId] ?: Score.ZERO

            val decayedPrevious = previousScore.applyDecay(DECAY_FACTOR)
            val weightedCurrent = currentScore.applyDecay(CURRENT_WEIGHT)
            decayedPrevious + weightedCurrent
        }
    }
}
