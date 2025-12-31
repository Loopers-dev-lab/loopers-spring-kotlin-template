package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductMonthlyRanking
import com.loopers.domain.ranking.ProductMonthlyRankingRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.time.YearMonth

@Repository
class ProductMonthlyRankingRepositoryImpl(
    private val productMonthlyRankingJpaRepository: ProductMonthlyRankingJpaRepository,
) : ProductMonthlyRankingRepository {

    override fun findByMonthPeriod(monthPeriod: YearMonth, pageable: Pageable): Page<ProductMonthlyRanking> {
        return productMonthlyRankingJpaRepository.findByMonthPeriod(monthPeriod, pageable)
    }
}
