package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductDailyMetric
import org.springframework.batch.item.ItemProcessor
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ScoreEntry - 점수 계산 결과
 *
 * @param productId 상품 ID
 * @param score 인기 점수
 */
data class ScoreEntry(
    val productId: Long,
    val score: BigDecimal,
)

/**
 * ScoreCalculationProcessor - 인기 점수 계산
 *
 * 점수 공식: viewCount * 0.1 + likeCount * 0.2 + orderAmount * 0.6
 *
 * - viewWeight: 0.1
 * - likeWeight: 0.2
 * - orderWeight: 0.6
 */
class ScoreCalculationProcessor : ItemProcessor<ProductDailyMetric, ScoreEntry> {

    companion object {
        private val VIEW_WEIGHT = BigDecimal("0.1")
        private val LIKE_WEIGHT = BigDecimal("0.2")
        private val ORDER_WEIGHT = BigDecimal("0.6")
        private const val SCALE = 2
    }

    override fun process(item: ProductDailyMetric): ScoreEntry {
        val viewScore = BigDecimal(item.viewCount).multiply(VIEW_WEIGHT)
        val likeScore = BigDecimal(item.likeCount).multiply(LIKE_WEIGHT)
        val orderScore = item.orderAmount.multiply(ORDER_WEIGHT)

        val totalScore = viewScore.add(likeScore).add(orderScore)
            .setScale(SCALE, RoundingMode.HALF_UP)

        return ScoreEntry(
            productId = item.productId,
            score = totalScore,
        )
    }
}
