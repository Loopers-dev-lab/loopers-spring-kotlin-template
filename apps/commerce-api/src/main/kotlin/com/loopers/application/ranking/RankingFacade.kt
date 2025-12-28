package com.loopers.application.ranking

import com.loopers.domain.product.ProductService
import com.loopers.support.cache.CacheKeys
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.stereotype.Component

@Component
class RankingFacade(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productService: ProductService,
) {

    private val zSetOps: ZSetOperations<String, String> = redisTemplate.opsForZSet()

    fun getRanking(command: RankingCommand.GetRankings): RankingInfo.Page {
        val pageable = command.pageable
        val key = CacheKeys.Ranking(command.date).key
        
        // 전체 랭킹 수 조회
        val totalElements = zSetOps.size(key) ?: 0L

        if (totalElements == 0L) {
            return RankingInfo.Page(
                items = emptyList(),
                pageNumber = pageable.pageNumber,
                pageSize = pageable.pageSize,
                totalElements = 0L,
            )
        }

        val start = (pageable.pageNumber * pageable.pageSize).toLong()
        val end = (start + pageable.pageSize - 1).coerceAtMost(totalElements - 1)

        val rankingWithScores = zSetOps.reverseRangeWithScores(key, start, end)
            ?: emptySet()

        val productIds = rankingWithScores.mapNotNull { it.value?.toLongOrNull() }

        if (productIds.isEmpty()) {
            return RankingInfo.Page(
                items = emptyList(),
                pageNumber = pageable.pageNumber,
                pageSize = pageable.pageSize,
                totalElements = totalElements,
            )
        }

        val productsWithBrand = productService.getProductsByIdsWithBrand(productIds)
        val productMap = productsWithBrand.associateBy { it.product.id }

        val rankingItems = rankingWithScores.mapIndexedNotNull { index, typedTuple ->
            val productId = typedTuple.value?.toLongOrNull() ?: return@mapIndexedNotNull null
            val productWithBrand = productMap[productId] ?: return@mapIndexedNotNull null

            val rank = (start + index + 1).toInt()

            val cacheRanking = RankingInfo.CacheRanking(
                rank = rank,
                productId = productId,
            )

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
