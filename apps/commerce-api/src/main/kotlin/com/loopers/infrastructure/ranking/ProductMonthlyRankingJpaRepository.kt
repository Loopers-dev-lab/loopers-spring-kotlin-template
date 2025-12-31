package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductMonthlyRanking
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.YearMonth

interface ProductMonthlyRankingJpaRepository : JpaRepository<ProductMonthlyRanking, Long> {
    fun findByMonthPeriod(monthPeriod: YearMonth, pageable: Pageable): Page<ProductMonthlyRanking>
}
