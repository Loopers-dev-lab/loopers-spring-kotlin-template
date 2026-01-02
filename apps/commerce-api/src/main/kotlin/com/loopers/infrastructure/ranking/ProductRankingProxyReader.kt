package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.ZoneId

/**
 * ProductRankingReader 통합 구현체
 *
 * 모든 RankingPeriod 값을 처리하는 단일 구현체입니다:
 * - HOURLY/DAILY: Redis ZSET에서 실시간 랭킹 데이터 조회
 * - WEEKLY/MONTHLY: RDB MV 테이블에서 집계된 랭킹 데이터 조회
 *
 * LSP 준수: 모든 RankingPeriod 값에 대해 예외 없이 동작합니다.
 * 캐싱 로직 없음: 순수 데이터 접근 계층 (Cache-Aside는 Facade 레이어에서 처리)
 */
@Repository
class ProductRankingProxyReader(
    private val redisTemplate: RedisTemplate<String, String>,
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
    private val rankingKeyGenerator: RankingKeyGenerator,
) : ProductRankingReader {

    private val zSetOps = redisTemplate.opsForZSet()

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }

    override fun findTopRankings(query: RankingQuery): List<ProductRanking> {
        return when (query.period) {
            RankingPeriod.HOURLY, RankingPeriod.DAILY -> findFromRedis(query)
            RankingPeriod.WEEKLY, RankingPeriod.MONTHLY -> findFromRdb(query)
        }
    }

    override fun findRankByProductId(query: RankingQuery, productId: Long): Int? {
        return when (query.period) {
            RankingPeriod.HOURLY, RankingPeriod.DAILY -> findRankFromRedis(query, productId)
            RankingPeriod.WEEKLY, RankingPeriod.MONTHLY -> findRankFromRdb(query, productId)
        }
    }

    override fun exists(query: RankingQuery): Boolean {
        return when (query.period) {
            RankingPeriod.HOURLY, RankingPeriod.DAILY -> existsInRedis(query)
            RankingPeriod.WEEKLY, RankingPeriod.MONTHLY -> existsInRdb(query)
        }
    }

    // ===== Redis (HOURLY/DAILY) =====

    private fun findFromRedis(query: RankingQuery): List<ProductRanking> {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime)
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

    private fun findRankFromRedis(query: RankingQuery, productId: Long): Int? {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime)
        val rank = zSetOps.reverseRank(bucketKey, productId.toString())
            ?: return null

        return (rank + 1).toInt()
    }

    private fun existsInRedis(query: RankingQuery): Boolean {
        val bucketKey = rankingKeyGenerator.bucketKey(query.period, query.dateTime)
        return redisTemplate.hasKey(bucketKey)
    }

    // ===== RDB (WEEKLY/MONTHLY) =====

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

            else -> emptyList() // This branch is never reached due to when routing
        }
    }

    private fun findRankFromRdb(query: RankingQuery, productId: Long): Int? {
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()

        return when (query.period) {
            RankingPeriod.WEEKLY -> {
                weeklyJpaRepository.findByBaseDateAndProductId(baseDate, productId)?.rank
            }

            RankingPeriod.MONTHLY -> {
                monthlyJpaRepository.findByBaseDateAndProductId(baseDate, productId)?.rank
            }

            else -> null // This branch is never reached due to when routing
        }
    }

    private fun existsInRdb(query: RankingQuery): Boolean {
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()

        return when (query.period) {
            RankingPeriod.WEEKLY -> weeklyJpaRepository.existsByBaseDate(baseDate)
            RankingPeriod.MONTHLY -> monthlyJpaRepository.existsByBaseDate(baseDate)
            else -> false // This branch is never reached due to when routing
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
