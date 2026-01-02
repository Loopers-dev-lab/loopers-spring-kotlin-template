package com.loopers.application.ranking

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.cache.CacheTemplate
import com.loopers.domain.product.ProductService
import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.RankingCommand
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingService
import com.loopers.domain.ranking.toQuery
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.ZoneId

@Component
class RankingFacade(
    private val rankingService: RankingService,
    private val productService: ProductService,
    private val cacheTemplate: CacheTemplate,
    private val clock: Clock,
) {
    companion object {
        private val TYPE_CACHED_RANKING_V1 = object : TypeReference<CachedRankingV1>() {}
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }

    /**
     * 인기 상품 랭킹 조회 (US-1)
     *
     * 1. RankingService를 통해 랭킹 조회 (폴백 로직 포함)
     * 2. ProductService를 통해 상품 정보 조회
     * 3. 랭킹 순서대로 결합하여 반환
     *
     * Cache-Aside: WEEKLY/MONTHLY 랭킹은 캐시를 통해 조회
     */
    @Transactional(readOnly = true)
    fun findRankings(criteria: RankingCriteria.FindRankings): RankingInfo.FindRankings {
        val command = criteria.toCommand()
        val rankings = if (command.period.shouldCache()) {
            findRankingsWithCache(command)
        } else {
            rankingService.findRankings(command)
        }

        if (rankings.isEmpty()) {
            return RankingInfo.FindRankings(
                rankings = emptyList(),
                hasNext = false,
            )
        }

        val hasNext = rankings.size > command.size
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
     * WEEKLY/MONTHLY 랭킹 조회 with Cache-Aside
     */
    private fun findRankingsWithCache(command: RankingCommand.FindRankings): List<ProductRanking> {
        val query = command.toQuery(clock)
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()

        val cacheKey = RankingCacheKeys.RankingList(
            period = command.period,
            baseDate = baseDate,
            offset = query.offset,
            limit = command.size,
        )

        // Cache hit
        val cached = cacheTemplate.get(cacheKey, TYPE_CACHED_RANKING_V1)
        if (cached != null) {
            return cached.toProductRankings()
        }

        // Cache miss
        val rankings = rankingService.findRankings(command)

        // Don't cache empty results
        if (rankings.isNotEmpty()) {
            cacheTemplate.put(cacheKey, CachedRankingV1.from(rankings))
        }

        return rankings
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

    /**
     * RankingPeriod가 캐시를 사용해야 하는지 확인
     */
    private fun RankingPeriod.shouldCache(): Boolean =
        this in listOf(RankingPeriod.WEEKLY, RankingPeriod.MONTHLY)
}
