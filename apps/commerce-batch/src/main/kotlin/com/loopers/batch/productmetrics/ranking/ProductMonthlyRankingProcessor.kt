package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.dto.ProductMonthlyMetricsAggregate
import com.loopers.domain.ranking.dto.RankedProduct
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 월간 상품 랭킹 Processor
 *
 * ProductMetricsAggregate를 받아서 가중치를 적용하여 RankedProduct로 변환
 *
 * 가중치 공식:
 * - 조회수 * viewWeight + 주문수 * soldWeight + 좋아요 * likeWeight
 */
@Component
@StepScope
class ProductMonthlyRankingProcessor(
    @Value("\${batch.ranking.weights.view}") private val viewWeight: Double,
    @Value("\${batch.ranking.weights.like}") private val likeWeight: Double,
    @Value("\${batch.ranking.weights.sold}") private val soldWeight: Double,
) : ItemProcessor<ProductMonthlyMetricsAggregate, RankedProduct> {

    override fun process(item: ProductMonthlyMetricsAggregate): RankedProduct {
        val finalScore = item.totalViewCount * viewWeight +
                item.totalSoldCount * soldWeight +
                item.totalLikeCount * likeWeight

        return RankedProduct(
            productId = item.productId,
            finalScore = finalScore,
        )
    }
}
