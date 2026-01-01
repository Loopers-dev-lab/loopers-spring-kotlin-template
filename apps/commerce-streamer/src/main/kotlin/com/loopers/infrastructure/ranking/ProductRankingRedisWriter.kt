package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingWriter
import com.loopers.domain.ranking.RankingKeyGenerator
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.Score
import org.springframework.data.redis.core.DefaultTypedTuple
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * ProductRankingRedisWriter - Redis ZSET 쓰기 구현
 *
 * - replaceAll: Staging key + RENAME으로 원자적 버킷 전환 (FR-4, AC-8)
 *
 * Atomic Transition Pattern (BR-4):
 * 1. 새 데이터를 staging key ({bucketKey}:staging)에 작성
 * 2. Redis RENAME으로 staging key를 active key로 원자적 교체
 * 3. 클라이언트는 전환 중에도 항상 완전한 데이터를 조회
 */
@Repository
class ProductRankingRedisWriter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingKeyGenerator: RankingKeyGenerator,
) : ProductRankingWriter {

    companion object {
        private val HOURLY_TTL_SECONDS = Duration.ofHours(2).seconds
        private val DAILY_TTL_SECONDS = Duration.ofHours(48).seconds
        private const val MAX_BUCKET_SIZE = 100L
        private const val STAGING_SUFFIX = ":staging"
    }

    /**
     * 전체 점수 교체 (Atomic Transition)
     *
     * Staging key + RENAME 패턴으로 원자적 버킷 전환 구현:
     * 1. staging key 클리어 (이전 실패한 시도가 있을 수 있음)
     * 2. staging key에 새 데이터 작성
     * 3. RENAME으로 staging -> active 원자적 교체
     * 4. period에 따른 TTL 설정 (HOURLY=2시간, DAILY=48시간)
     *
     * @param period 랭킹 기간 (HOURLY, DAILY)
     * @param dateTime 버킷 기준 시간
     * @param scores 상품ID -> 점수 맵
     */
    override fun replaceAll(period: RankingPeriod, dateTime: ZonedDateTime, scores: Map<Long, Score>) {
        val bucketKey = rankingKeyGenerator.bucketKey(period, dateTime)

        if (scores.isEmpty()) {
            redisTemplate.delete(bucketKey)
            return
        }

        val stagingKey = "$bucketKey$STAGING_SUFFIX"
        val zSetOps = redisTemplate.opsForZSet()

        // 1. Staging key 클리어 (이전 실패한 시도가 있을 경우 대비)
        redisTemplate.delete(stagingKey)

        // 2. Staging key에 새 데이터 작성
        val tuples = scores.map { (productId, score) ->
            DefaultTypedTuple(productId.toString(), score.value.toDouble())
        }.toSet()
        zSetOps.add(stagingKey, tuples)

        // Keep only top 100: remove all except ranks 0~99 (highest scores)
        zSetOps.removeRange(stagingKey, 0, -(MAX_BUCKET_SIZE + 1))

        // 3. RENAME으로 staging -> active 원자적 교체
        redisTemplate.rename(stagingKey, bucketKey)

        // 4. Period에 따른 TTL 설정
        val ttlSeconds = determineTtl(period)
        redisTemplate.expire(bucketKey, ttlSeconds, TimeUnit.SECONDS)
    }

    /**
     * period에 따른 TTL 결정
     *
     * - HOURLY -> 2시간 TTL
     * - DAILY -> 48시간 TTL
     */
    private fun determineTtl(period: RankingPeriod): Long {
        return when (period) {
            RankingPeriod.HOURLY -> HOURLY_TTL_SECONDS
            RankingPeriod.DAILY -> DAILY_TTL_SECONDS
        }
    }
}
