package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingKeyGenerator
import com.loopers.domain.ranking.RankingQuery
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.ZoneId

@Repository
class ProductRankingRedisReader(
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingKeyGenerator: RankingKeyGenerator,
) : ProductRankingReader {

    private val zSetOps = redisTemplate.opsForZSet()

    override fun findTopRankings(query: RankingQuery): List<ProductRanking> {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime.atZone(SEOUL_ZONE))
        return findFromBucket(bucketKey, query)
    }

    private fun findFromBucket(bucketKey: String, query: RankingQuery): List<ProductRanking> {
        // limit + 1 for hasNext determination
        val limit = query.limit + 1
        val end = query.offset + limit - 1

        val result = zSetOps.reverseRangeWithScores(bucketKey, query.offset, end)
            ?: return emptyList()

        return result.mapIndexedNotNull { index, typedTuple ->
            val productIdStr = typedTuple.value ?: return@mapIndexedNotNull null
            val score = typedTuple.score ?: return@mapIndexedNotNull null

            ProductRanking(
                productId = productIdStr.toLongOrNull() ?: return@mapIndexedNotNull null,
                rank = (query.offset + index + 1).toInt(),
                score = BigDecimal.valueOf(score),
            )
        }
    }

    override fun findRankByProductId(query: RankingQuery, productId: Long): Int? {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime.atZone(SEOUL_ZONE))
        val rank = zSetOps.reverseRank(bucketKey, productId.toString())
            ?: return null

        return (rank + 1).toInt()
    }

    override fun exists(query: RankingQuery): Boolean {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime.atZone(SEOUL_ZONE))
        return redisTemplate.hasKey(bucketKey)
    }

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }
}
