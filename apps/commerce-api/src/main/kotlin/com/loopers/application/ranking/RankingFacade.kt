package com.loopers.application.ranking

import com.loopers.domain.product.ProductService
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingKeyGenerator
import com.loopers.domain.ranking.RankingService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RankingFacade(
    private val rankingService: RankingService,
    private val productService: ProductService,
    private val productRankingReader: ProductRankingReader,
) {

    companion object {
        private const val RANKING_KEY_PREFIX = "ranking:products:"
    }

    /**
     * 인기 상품 랭킹 조회 (US-1)
     *
     * 1. Redis에서 상위 N개 productId 조회
     * 2. ProductService를 통해 상품 정보 조회
     * 3. 랭킹 순서대로 결합하여 반환
     */
    @Transactional(readOnly = true)
    fun findRankings(criteria: RankingCriteria.FindRankings): RankingInfo.FindRankings {
        val bucketKey = resolveBucketKey(criteria.date)
        val page = criteria.resolvedPage()
        val size = criteria.resolvedSize()

        val offset = (page * size).toLong()
        val limit = (size + 1).toLong()

        val rankings = productRankingReader.getTopRankings(bucketKey, offset, limit)

        if (rankings.isEmpty()) {
            return RankingInfo.FindRankings(
                rankings = emptyList(),
                hasNext = false,
            )
        }

        val hasNext = rankings.size > size
        val paginatedRankings = if (hasNext) rankings.dropLast(1) else rankings

        val productIds = paginatedRankings.map { it.productId }
        val productViews = productService.findAllProductViewByIds(productIds)
        val productViewMap = productViews.associateBy { it.productId }

        val rankingUnits = paginatedRankings.mapNotNull { ranking ->
            productViewMap[ranking.productId]?.let { productView ->
                RankingInfo.RankingUnit.from(
                    rank = ranking.rank,
                    productView = productView,
                )
            }
        }

        return RankingInfo.FindRankings(
            rankings = rankingUnits,
            hasNext = hasNext,
        )
    }

    /**
     * 가중치 조회
     */
    @Transactional(readOnly = true)
    fun findWeight(): RankingInfo.FindWeight {
        val weight = rankingService.findWeight()
        return RankingInfo.FindWeight.from(weight)
    }

    /**
     * 가중치 수정 (US-3)
     */
    @Transactional
    fun updateWeight(criteria: RankingCriteria.UpdateWeight): RankingInfo.UpdateWeight {
        val updatedWeight = rankingService.updateWeight(
            viewWeight = criteria.viewWeight,
            likeWeight = criteria.likeWeight,
            orderWeight = criteria.orderWeight,
        )
        return RankingInfo.UpdateWeight.from(updatedWeight)
    }

    private fun resolveBucketKey(date: String?): String {
        return if (date != null) {
            "$RANKING_KEY_PREFIX$date"
        } else {
            RankingKeyGenerator.currentBucketKey()
        }
    }
}
