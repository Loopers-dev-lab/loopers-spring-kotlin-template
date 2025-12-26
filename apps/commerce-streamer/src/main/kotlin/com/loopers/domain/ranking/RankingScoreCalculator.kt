package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

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
    }

    /**
     * CountSnapshot과 가중치를 기반으로 Score 계산
     *
     * Score = viewCount x viewWeight + likeCount x likeWeight + orderAmount x orderWeight
     *
     * @param snapshot 집계된 카운트 스냅샷
     * @param weight 가중치 설정
     * @return 계산된 Score
     */
    fun calculate(snapshot: CountSnapshot, weight: RankingWeight): Score {
        val viewScore = BigDecimal.valueOf(snapshot.views)
            .multiply(weight.viewWeight)

        val likeScore = BigDecimal.valueOf(snapshot.likes)
            .multiply(weight.likeWeight)

        val orderScore = snapshot.orderAmount
            .multiply(weight.orderWeight)

        val totalScore = viewScore
            .add(likeScore)
            .add(orderScore)
            .setScale(SCALE, ROUNDING_MODE)

        // Score는 음수가 될 수 없으므로 0 이상으로 보정
        return Score.of(maxOf(totalScore, BigDecimal.ZERO))
    }

    /**
     * 감쇠(decay)를 적용한 Score 계산 (버킷 전환 시 사용)
     *
     * newScore = currentScore x 1.0 + previousScore x decayFactor
     *
     * @param currentScore 현재 버킷의 점수
     * @param previousScore 이전 버킷의 점수
     * @param decayFactor 감쇠 계수 (기본값: 0.1)
     * @return 감쇠가 적용된 새로운 Score
     */
    fun calculateWithDecay(
        currentScore: Score,
        previousScore: Score,
        decayFactor: BigDecimal,
    ): Score {
        val decayedPreviousScore = previousScore.applyDecay(decayFactor)
        return currentScore + decayedPreviousScore
    }
}
