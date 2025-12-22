package com.loopers.application.ranking

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.domain.product.ProductService
import com.loopers.support.cache.CacheKeys
import com.loopers.support.cache.CacheTemplate
import org.springframework.stereotype.Component

@Component
class RankingFacade(
    private val cacheTemplate: CacheTemplate,
    private val productService: ProductService,
) {

    companion object {
        private val RANKING_CACHE_TYPE = object : TypeReference<List<RankingInfo.CacheRanking>>() {}
    }

    fun getRanking(command: RankingCommand.GetRankings): RankingInfo.Page {
        val cacheKey = CacheKeys.Ranking(command.date)
        //  val cachedRanking = cacheTemplate.get(cacheKey, RANKING_CACHE_TYPE) ?: emptyList()
        val cachedRanking: List<RankingInfo.CacheRanking> = emptyList()

        // 페이징 처리
        val pageable = command.pageable
        val totalElements = cachedRanking.size.toLong()
        val start = (pageable.pageNumber * pageable.pageSize).toInt().coerceAtMost(cachedRanking.size)
        val end = (start + pageable.pageSize).coerceAtMost(cachedRanking.size)
        val pagedRanking = cachedRanking.subList(start, end)

        val productIds = pagedRanking.map { it.productId }
        val productsWithBrand = productService.getProductsByIdsWithBrand(productIds)

        val productMap = productsWithBrand.associateBy { it.product.id }

        val rankingItems = pagedRanking.mapNotNull { cacheRanking ->
            val productWithBrand = productMap[cacheRanking.productId] ?: return@mapNotNull null
            RankingInfo.RankingItem.from(cacheRanking, productWithBrand)
        }

        return RankingInfo.Page(
            items = rankingItems,
            pageNumber = pageable.pageNumber,
            pageSize = pageable.pageSize,
            totalElements = totalElements,
        )
    }
}
