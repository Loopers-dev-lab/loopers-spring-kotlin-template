package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.ZoneId

/**
 * RDB 기반 랭킹 조회 구현체 (Cache-Aside 패턴 적용)
 *
 * WEEKLY/MONTHLY 랭킹 조회를 위한 구현체입니다.
 * mv_product_rank_weekly 및 mv_product_rank_monthly 테이블에서 데이터를 조회합니다.
 *
 * Cache-Aside 패턴:
 * 1. Redis 캐시 조회
 * 2. 캐시 미스 시 RDB 조회
 * 3. RDB 결과를 Redis에 캐싱 (TTL: 1시간)
 *
 * Note: 이 클래스는 @Component로 등록되며, CompositeProductRankingReader가
 * ProductRankingReader 인터페이스의 단일 @Repository 빈으로 동작합니다.
 */
@Component
class ProductRankingRdbReader(
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingKeyGenerator: RankingKeyGenerator,
) : ProductRankingReader {

    private val zSetOps = redisTemplate.opsForZSet()

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
        private val CACHE_TTL = Duration.ofHours(1)
        private const val CACHE_SUFFIX = ":cache"
    }

    override fun findTopRankings(query: RankingQuery): List<ProductRanking> {
        val cacheKey = getCacheKey(query)

        // 1. Try cache first
        val cachedResult = findFromCache(cacheKey, query)
        if (cachedResult.isNotEmpty()) {
            return cachedResult
        }

        // 2. Cache miss - query from RDB
        val rdbResult = findFromRdb(query)

        // 3. Cache the result if not empty
        if (rdbResult.isNotEmpty()) {
            cacheRankings(cacheKey, rdbResult)
        }

        return rdbResult
    }

    override fun findRankByProductId(query: RankingQuery, productId: Long): Int? {
        val cacheKey = getCacheKey(query)

        // Try cache first
        val cachedRank = findRankFromCache(cacheKey, productId)
        if (cachedRank != null) {
            return cachedRank
        }

        // Cache miss - query from RDB
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()

        return when (query.period) {
            RankingPeriod.WEEKLY -> {
                weeklyJpaRepository.findByBaseDateAndProductId(baseDate, productId)?.rank
            }

            RankingPeriod.MONTHLY -> {
                monthlyJpaRepository.findByBaseDateAndProductId(baseDate, productId)?.rank
            }

            else -> {
                throw IllegalArgumentException("ProductRankingRdbReader does not support period: ${query.period}")
            }
        }
    }

    override fun exists(query: RankingQuery): Boolean {
        val cacheKey = getCacheKey(query)

        // Check cache first
        if (redisTemplate.hasKey(cacheKey)) {
            return true
        }

        // Check RDB
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()

        return when (query.period) {
            RankingPeriod.WEEKLY -> weeklyJpaRepository.existsByBaseDate(baseDate)
            RankingPeriod.MONTHLY -> monthlyJpaRepository.existsByBaseDate(baseDate)
            else -> throw IllegalArgumentException("ProductRankingRdbReader does not support period: ${query.period}")
        }
    }

    private fun getCacheKey(query: RankingQuery): String {
        val baseKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime)
        return "$baseKey$CACHE_SUFFIX"
    }

    private fun findFromCache(cacheKey: String, query: RankingQuery): List<ProductRanking> {
        val limit = query.limit + 1
        val end = query.offset + limit - 1

        val result = zSetOps.reverseRangeWithScores(cacheKey, query.offset, end)
            ?: return emptyList()

        if (result.isEmpty()) {
            return emptyList()
        }

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

    private fun findRankFromCache(cacheKey: String, productId: Long): Int? {
        val rank = zSetOps.reverseRank(cacheKey, productId.toString())
            ?: return null
        return (rank + 1).toInt()
    }

    private fun findFromRdb(query: RankingQuery): List<ProductRanking> {
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()
        val limit = (query.limit + 1).toInt()
        val offset = query.offset.toInt()
        val pageable = PageRequest.of(0, offset + limit)

        return when (query.period) {
            RankingPeriod.WEEKLY -> {
                weeklyJpaRepository.findByBaseDateOrderByRankAsc(baseDate, pageable)
                    .drop(offset)
                    .map { toProductRanking(it.productId, it.rank, it.score) }
            }

            RankingPeriod.MONTHLY -> {
                monthlyJpaRepository.findByBaseDateOrderByRankAsc(baseDate, pageable)
                    .drop(offset)
                    .map { toProductRanking(it.productId, it.rank, it.score) }
            }

            else -> {
                throw IllegalArgumentException("ProductRankingRdbReader does not support period: ${query.period}")
            }
        }
    }

    private fun cacheRankings(cacheKey: String, rankings: List<ProductRanking>) {
        // Store all rankings in sorted set (not just the current page)
        // This ensures full cache coverage for pagination
        val tuples = rankings.map { ranking ->
            org.springframework.data.redis.core.ZSetOperations.TypedTuple.of(
                ranking.productId.toString(),
                ranking.score.toDouble(),
            )
        }.toSet()

        if (tuples.isNotEmpty()) {
            zSetOps.add(cacheKey, tuples)
            redisTemplate.expire(cacheKey, CACHE_TTL)
        }
    }

    private fun toProductRanking(
        productId: Long,
        rank: Int,
        score: BigDecimal,
    ): ProductRanking {
        return ProductRanking(
            productId = productId,
            rank = rank,
            score = score,
        )
    }
}
