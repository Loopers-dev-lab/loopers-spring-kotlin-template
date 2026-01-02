package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingQuery
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Redis 기반 랭킹 조회 구현체
 *
 * HOURLY/DAILY 랭킹 조회를 위한 구현체입니다.
 * Redis ZSET에서 실시간 랭킹 데이터를 조회합니다.
 *
 * Note: 이 클래스는 @Component로 등록되며, CompositeProductRankingReader가
 * ProductRankingReader 인터페이스의 단일 @Repository 빈으로 동작합니다.
 */
@Component
class ProductRankingRedisReader(
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingKeyGenerator: RankingKeyGenerator,
) : ProductRankingReader {

    private val zSetOps = redisTemplate.opsForZSet()

    override fun findTopRankings(query: RankingQuery): List<ProductRanking> {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime)
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
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime)
        val rank = zSetOps.reverseRank(bucketKey, productId.toString())
            ?: return null

        return (rank + 1).toInt()
    }

    override fun exists(query: RankingQuery): Boolean {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime)
        return redisTemplate.hasKey(bucketKey)
    }
}
