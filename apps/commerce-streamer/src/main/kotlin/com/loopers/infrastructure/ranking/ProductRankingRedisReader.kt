package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.Score
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

/**
 * ProductRankingRedisReader - Redis ZSET 읽기 구현
 *
 * - getAllScores: ZRANGE bucketKey 0 -1 WITHSCORES로 전체 조회
 * - exists: EXISTS 명령으로 버킷 존재 확인
 */
@Repository
class ProductRankingRedisReader(
    private val redisTemplate: RedisTemplate<String, String>,
) : ProductRankingReader {

    private val zSetOps = redisTemplate.opsForZSet()

    override fun getAllScores(bucketKey: String): Map<Long, Score> {
        val result = zSetOps.rangeWithScores(bucketKey, 0, -1)
            ?: return emptyMap()

        return result.mapNotNull { typedTuple ->
            val productIdStr = typedTuple.value ?: return@mapNotNull null
            val score = typedTuple.score ?: return@mapNotNull null
            val productId = productIdStr.toLongOrNull() ?: return@mapNotNull null

            productId to Score.of(score)
        }.toMap()
    }

    override fun exists(bucketKey: String): Boolean {
        return redisTemplate.hasKey(bucketKey) == true
    }
}
