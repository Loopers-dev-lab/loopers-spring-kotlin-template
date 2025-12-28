package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductWeeklyRanking
import com.loopers.domain.ranking.ProductWeeklyRankingRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 주간 상품 랭킹 Repository 구현체
 */
@Repository
class ProductWeeklyRankingRepositoryImpl(
    private val productWeeklyRankingJpaRepository: ProductWeeklyRankingJpaRepository,
) : ProductWeeklyRankingRepository {

    override fun saveAll(weeklyRankings: List<ProductWeeklyRanking>): List<ProductWeeklyRanking> {
        return productWeeklyRankingJpaRepository.saveAll(weeklyRankings)
    }

    override fun findByWeekStartAndWeekEnd(weekStart: LocalDate, weekEnd: LocalDate): List<ProductWeeklyRanking> {
        return productWeeklyRankingJpaRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd)
    }

    override fun findByProductIdAndWeekStartAndWeekEnd(
        productId: Long,
        weekStart: LocalDate,
        weekEnd: LocalDate,
    ): List<ProductWeeklyRanking> {
        return productWeeklyRankingJpaRepository.findByProductIdAndWeekStartAndWeekEnd(
            productId,
            weekStart,
            weekEnd,
        )
    }

    override fun findByWeekRange(monthStart: LocalDate, monthEnd: LocalDate): List<ProductWeeklyRanking> {
        return productWeeklyRankingJpaRepository.findByWeekRange(monthStart, monthEnd)
    }

    override fun deleteByWeekStartAndWeekEnd(weekStart: LocalDate, weekEnd: LocalDate) {
        productWeeklyRankingJpaRepository.deleteByWeekStartAndWeekEnd(weekStart, weekEnd)
    }
}
