package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductMonthlyRanking
import com.loopers.domain.ranking.ProductMonthlyRankingRepository
import java.time.YearMonth
import org.springframework.stereotype.Repository

@Repository
class ProductMonthlyRankingRepositoryImpl(
    private val productMonthlyRankingJpaRepository: ProductMonthlyRankingJpaRepository,
) : ProductMonthlyRankingRepository {

    override fun saveAll(monthlyRankings: List<ProductMonthlyRanking>): List<ProductMonthlyRanking> {
        return productMonthlyRankingJpaRepository.saveAll(monthlyRankings)
    }

    override fun findByMonthPeriod(monthPeriod: YearMonth): List<ProductMonthlyRanking> {
        return productMonthlyRankingJpaRepository.findByMonthPeriod(monthPeriod)
    }

    override fun deleteByMonthPeriod(monthPeriod: YearMonth) {
        productMonthlyRankingJpaRepository.deleteByMonthPeriod(monthPeriod)
    }
}
