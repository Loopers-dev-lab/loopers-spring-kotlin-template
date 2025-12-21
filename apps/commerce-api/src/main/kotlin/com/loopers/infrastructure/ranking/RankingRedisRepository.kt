package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScore
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

/**
 * Redis ZSET을 활용한 랭킹 조회 Repository 구현체
 *
 * ZSET Key 패턴: ranking:all:{yyyyMMdd}
 */
@Repository
class RankingRedisRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {

    private val log = LoggerFactory.getLogger(RankingRedisRepository::class.java)

    companion object {
        private const val KEY_PREFIX = "ranking:all"
        private const val MEMBER_PAD_LENGTH = 15
    }

    override fun getScores(dateKey: String, start: Long, end: Long): List<RankingScore> {
        val key = buildKey(dateKey)

        return try {
            val tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end).orEmpty()
            tuples.mapNotNull { tuple ->
                val member = tuple.value ?: return@mapNotNull null
                val score = tuple.score ?: return@mapNotNull null
                member.toLongOrNull()?.let { RankingScore(it, score) }
            }
        } catch (e: Exception) {
            log.error("랭킹 점수 조회 실패: key={}, start={}, end={}", key, start, end, e)
            emptyList()
        }
    }

    override fun getScore(dateKey: String, productId: Long): Double? {
        val key = buildKey(dateKey)
        val member = toMember(productId)

        return try {
            redisTemplate.opsForZSet().score(key, member)
        } catch (e: Exception) {
            log.error("랭킹 점수 조회 실패: key={}, productId={}", key, productId, e)
            null
        }
    }

    override fun getTotalCount(dateKey: String): Long {
        val key = buildKey(dateKey)

        return try {
            redisTemplate.opsForZSet().zCard(key) ?: 0L
        } catch (e: Exception) {
            log.error("랭킹 전체 개수 조회 실패: key={}", key, e)
            0L
        }
    }

    override fun getRank(dateKey: String, productId: Long): Long? {
        val key = buildKey(dateKey)
        val member = toMember(productId)

        return try {
            redisTemplate.opsForZSet().reverseRank(key, member)
        } catch (e: Exception) {
            log.error("랭킹 순위 조회 실패: key={}, productId={}", key, productId, e)
            null
        }
    }

    private fun buildKey(dateKey: String): String {
        return "$KEY_PREFIX:$dateKey"
    }

    private fun toMember(productId: Long): String {
        return productId.toString().padStart(MEMBER_PAD_LENGTH, '0')
    }
}
