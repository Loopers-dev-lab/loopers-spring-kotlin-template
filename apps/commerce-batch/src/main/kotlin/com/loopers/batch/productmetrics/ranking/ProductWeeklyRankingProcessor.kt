package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.dto.ProductWeeklyMetricsAggregate
import com.loopers.domain.ranking.dto.RankedProduct
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 주간 상품 랭킹 Processor
 *
 * ProductWeeklyMetricsAggregate를 받아서 날짜별 감쇠 가중치를 적용하여 점수 계산
 *
 * 가중치 공식:
 * - (조회수 * viewWeight + 주문수 * soldWeight + 좋아요 * likeWeight) * 날짜별 감쇠 가중치
 *
 * 날짜별 감쇠 가중치:
 * - D+0 (daysFromEnd=0): 1.0
 * - D+1 (daysFromEnd=1): 0.9
 * - D+2 (daysFromEnd=2): 0.8
 * - D+3 (daysFromEnd=3): 0.4
 * - D+4 (daysFromEnd=4): 0.3
 * - D+5 (daysFromEnd=5): 0.2
 * - D+6 (daysFromEnd=6): 0.1
 */
@Component
@StepScope
class ProductWeeklyRankingProcessor(
    @Value("\${batch.ranking.weights.view:1}") private val viewWeight: Double,
    @Value("\${batch.ranking.weights.like:5}") private val likeWeight: Double,
    @Value("\${batch.ranking.weights.sold:10}") private val soldWeight: Double,
) : ItemProcessor<ProductWeeklyMetricsAggregate, RankedProduct> {

    override fun process(item: ProductWeeklyMetricsAggregate): RankedProduct {
        // 날짜별 감쇠 가중치 계산
        val decayWeight = when (item.daysFromEnd) {
            0 -> 1.0
            1 -> 0.9
            2 -> 0.8
            3 -> 0.4
            4 -> 0.3
            5 -> 0.2
            6 -> 0.1
            else -> 0.0
        }

        // 기본 점수 계산
        val baseScore = item.viewCount * viewWeight +
                item.soldCount * soldWeight +
                item.likeCount * likeWeight

        // 감쇠 가중치를 적용한 최종 점수
        val finalScore = baseScore * decayWeight

        return RankedProduct(
            productId = item.productId,
            finalScore = finalScore,
        )
    }
}
