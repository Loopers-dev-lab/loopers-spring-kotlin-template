package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

/**
 * Composite ProductRankingReader
 *
 * 기간 타입에 따라 적절한 Reader로 위임합니다:
 * - HOURLY/DAILY: Redis 기반 조회 (ProductRankingRedisReader)
 * - WEEKLY/MONTHLY: RDB 기반 조회 (ProductRankingRdbReader)
 *
 * @Primary 어노테이션을 통해 ProductRankingReader 타입으로 주입할 때
 * 기본적으로 이 빈이 선택됩니다.
 */
@Primary
@Repository
class CompositeProductRankingReader(
    private val redisReader: ProductRankingRedisReader,
    private val rdbReader: ProductRankingRdbReader,
) : ProductRankingReader {

    override fun findTopRankings(query: RankingQuery): List<ProductRanking> {
        return getReader(query.period).findTopRankings(query)
    }

    override fun findRankByProductId(query: RankingQuery, productId: Long): Int? {
        return getReader(query.period).findRankByProductId(query, productId)
    }

    override fun exists(query: RankingQuery): Boolean {
        return getReader(query.period).exists(query)
    }

    private fun getReader(period: RankingPeriod): ProductRankingReader {
        return when (period) {
            RankingPeriod.HOURLY, RankingPeriod.DAILY -> redisReader
            RankingPeriod.WEEKLY, RankingPeriod.MONTHLY -> rdbReader
        }
    }
}
