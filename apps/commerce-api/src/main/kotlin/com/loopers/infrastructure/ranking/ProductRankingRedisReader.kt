package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class ProductRankingRedisReader(
    private val redisTemplate: RedisTemplate<String, String>,
) : ProductRankingReader {

    private val zSetOps = redisTemplate.opsForZSet()

    override fun getTopRankings(bucketKey: String, offset: Long, limit: Long): List<ProductRanking> {
        val end = offset + limit - 1

        val result = zSetOps.reverseRangeWithScores(bucketKey, offset, end)
            ?: return emptyList()

        return result.mapIndexedNotNull { index, typedTuple ->
            val productIdStr = typedTuple.value ?: return@mapIndexedNotNull null
            val score = typedTuple.score ?: return@mapIndexedNotNull null

            ProductRanking(
                productId = productIdStr.toLongOrNull() ?: return@mapIndexedNotNull null,
                rank = (offset + index + 1).toInt(),
                score = BigDecimal.valueOf(score),
            )
        }
    }

    override fun getRankByProductId(bucketKey: String, productId: Long): Int? {
        val rank = zSetOps.reverseRank(bucketKey, productId.toString())
            ?: return null

        return (rank + 1).toInt()
    }
}
