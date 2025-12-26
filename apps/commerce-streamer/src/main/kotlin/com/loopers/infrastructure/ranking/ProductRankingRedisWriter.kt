package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingWriter
import com.loopers.domain.ranking.Score
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

/**
 * ProductRankingRedisWriter - Redis ZSET 쓰기 구현
 *
 * - incrementScores: Pipeline + ZINCRBY로 배치 점수 증분 (TTL 자동 설정)
 * - replaceAll: DEL + ZADD로 전체 교체
 * - createBucket: ZADD로 새 버킷 생성
 */
@Repository
class ProductRankingRedisWriter(
    private val redisTemplate: RedisTemplate<String, String>,
) : ProductRankingWriter {

    companion object {
        private val TTL_SECONDS = java.time.Duration.ofHours(2).seconds
    }

    override fun incrementScores(bucketKey: String, deltas: Map<Long, Score>) {
        if (deltas.isEmpty()) return

        val keyBytes = bucketKey.toByteArray()
        redisTemplate.executePipelined(
            RedisCallback<Any> { connection ->
                deltas.forEach { (productId, score) ->
                    connection.zSetCommands().zIncrBy(
                        keyBytes,
                        score.value.toDouble(),
                        productId.toString().toByteArray(),
                    )
                }
                connection.keyCommands().expire(keyBytes, TTL_SECONDS)
                null
            },
        )
    }

    override fun replaceAll(bucketKey: String, scores: Map<Long, Score>) {
        if (scores.isEmpty()) {
            redisTemplate.delete(bucketKey)
            return
        }

        // Delete first, then add all scores and set TTL
        redisTemplate.delete(bucketKey)

        val zSetOps = redisTemplate.opsForZSet()
        val tuples = scores.map { (productId, score) ->
            org.springframework.data.redis.core.DefaultTypedTuple(
                productId.toString(),
                score.value.toDouble(),
            )
        }.toSet()

        zSetOps.add(bucketKey, tuples)
        redisTemplate.expire(bucketKey, TTL_SECONDS, TimeUnit.SECONDS)
    }

    override fun createBucket(bucketKey: String, scores: Map<Long, Score>) {
        if (scores.isEmpty()) return

        val zSetOps = redisTemplate.opsForZSet()
        val tuples = scores.map { (productId, score) ->
            org.springframework.data.redis.core.DefaultTypedTuple(
                productId.toString(),
                score.value.toDouble(),
            )
        }.toSet()

        zSetOps.add(bucketKey, tuples)
        redisTemplate.expire(bucketKey, TTL_SECONDS, TimeUnit.SECONDS)
    }
}
