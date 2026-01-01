package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.ZoneId

/**
 * RDB 기반 랭킹 조회 구현체
 *
 * WEEKLY/MONTHLY 랭킹 조회를 위한 구현체입니다.
 * mv_product_rank_weekly 및 mv_product_rank_monthly 테이블에서 데이터를 조회합니다.
 *
 * Note: 이 클래스는 @Component로 등록되며, CompositeProductRankingReader가
 * ProductRankingReader 인터페이스의 단일 @Repository 빈으로 동작합니다.
 */
@Component
class ProductRankingRdbReader(
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
) : ProductRankingReader {

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }

    override fun findTopRankings(query: RankingQuery): List<ProductRanking> {
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()
        // limit + 1 for hasNext determination (same pattern as ProductRankingRedisReader)
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

    override fun findRankByProductId(query: RankingQuery, productId: Long): Int? {
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
        val baseDate = query.dateTime.atZone(SEOUL_ZONE).toLocalDate()

        return when (query.period) {
            RankingPeriod.WEEKLY -> weeklyJpaRepository.existsByBaseDate(baseDate)
            RankingPeriod.MONTHLY -> monthlyJpaRepository.existsByBaseDate(baseDate)
            else -> throw IllegalArgumentException("ProductRankingRdbReader does not support period: ${query.period}")
        }
    }

    private fun toProductRanking(
        productId: Long,
        rank: Int,
        score: java.math.BigDecimal,
    ): ProductRanking {
        return ProductRanking(
            productId = productId,
            rank = rank,
            score = score,
        )
    }
}
