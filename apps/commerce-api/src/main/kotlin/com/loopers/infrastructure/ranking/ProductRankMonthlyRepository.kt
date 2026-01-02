package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankMonthly
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankMonthlyRepository : JpaRepository<ProductRankMonthly, Long> {
    fun findByPeriodOrderByRankPositionAsc(period: String): List<ProductRankMonthly>
}
