package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.Ranking
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.stereotype.Repository
import java.time.Duration

/**
 * Redis ZSET 기반 랭킹 저장소 구현체
 */
@Repository
class RankingRedisRepository(
    @param:Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    companion object {
        private val logger = LoggerFactory.getLogger(RankingRedisRepository::class.java)
    }

    private val zSetOps: ZSetOperations<String, String> = redisTemplate.opsForZSet()

    override fun incrementScore(key: RankingKey, productId: Long, score: RankingScore): Double {
        val redisKey = key.toRedisKey()
        val member = productId.toString()

        // ZINCRBY: 점수 증가 (기존 값에 더하기)
        val newScore = zSetOps.incrementScore(redisKey, member, score.value)
            ?: throw IllegalStateException("ZINCRBY 실패: key=$redisKey, member=$member")

        logger.debug("랭킹 점수 증가: key=$redisKey, productId=$productId, increment=${score.value}, newScore=$newScore")
        return newScore
    }

    override fun incrementScoreBatch(key: RankingKey, scoreMap: Map<Long, RankingScore>) {
        if (scoreMap.isEmpty()) return

        val redisKey = key.toRedisKey()

        scoreMap.forEach { (productId, score) ->
            val member = productId.toString()
            zSetOps.incrementScore(redisKey, member, score.value)
        }

        logger.debug("랭킹 점수 배치 증가: key=$redisKey, count=${scoreMap.size}")
    }

    override fun getTopN(key: RankingKey, start: Int, end: Int): List<Ranking> {
        val redisKey = key.toRedisKey()

        // ZREVRANGE: score 기준 내림차순으로 범위 조회 (높은 점수부터)
        val items = zSetOps.reverseRangeWithScores(redisKey, start.toLong(), end.toLong())
            ?: emptySet()

        return items.mapIndexed { index, item ->
            val productId = item.value?.toLongOrNull()
                ?: throw IllegalStateException("Invalid productId: ${item.value}")
            val score = item.score ?: 0.0
            val rank = start + index + 1 // 순위는 1부터 시작

            Ranking.from(productId, score, rank)
        }
    }

    override fun getRank(key: RankingKey, productId: Long): Int? {
        val redisKey = key.toRedisKey()
        val member = productId.toString()

        // ZREVRANK: 내림차순 순위 조회 (0부터 시작)
        val rank = zSetOps.reverseRank(redisKey, member) ?: return null

        // 1부터 시작하도록 변환
        return rank.toInt() + 1
    }

    override fun getScore(key: RankingKey, productId: Long): RankingScore? {
        val redisKey = key.toRedisKey()
        val member = productId.toString()

        // ZSCORE: 점수 조회
        val score = zSetOps.score(redisKey, member) ?: return null

        return RankingScore(score)
    }

    override fun getCount(key: RankingKey): Long {
        val redisKey = key.toRedisKey()

        // ZCARD: 멤버 수 조회
        return zSetOps.size(redisKey) ?: 0L
    }

    override fun setExpire(key: RankingKey) {
        val redisKey = key.toRedisKey()
        val ttl = Duration.ofDays(key.window.ttlDays.toLong())

        redisTemplate.expire(redisKey, ttl)
        logger.debug("랭킹 TTL 설정: key=$redisKey, ttl=$ttl")
    }

    override fun copyWithWeight(sourceKey: RankingKey, targetKey: RankingKey, weight: Double) {
        val sourceRedisKey = sourceKey.toRedisKey()
        val targetRedisKey = targetKey.toRedisKey()

        // 원본 ZSET의 모든 항목을 조회하여 가중치를 곱해서 대상 ZSET에 복사
        val items = zSetOps.reverseRangeWithScores(sourceRedisKey, 0, -1) ?: emptySet()

        if (items.isEmpty()) {
            logger.warn("콜드 스타트 방지: 원본 랭킹 데이터가 없음 - source=$sourceRedisKey")
            return
        }

        // 각 항목의 점수에 가중치를 곱해서 대상 ZSET에 추가
        items.forEach { item ->
            val member = item.value ?: return@forEach
            val originalScore = item.score ?: return@forEach
            val newScore = originalScore * weight

            zSetOps.add(targetRedisKey, member, newScore)
        }

        logger.info(
            "랭킹 데이터 복사 완료 (콜드 스타트 방지): " +
                    "source=$sourceRedisKey, target=$targetRedisKey, weight=$weight, count=${items.size}",
        )
    }
}
