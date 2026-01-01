package com.loopers.infrastructure

import com.loopers.domain.ranking.ProductRankMonthly
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductRankMonthlyRepository : JpaRepository<ProductRankMonthly, Long> {
    @Modifying
    @Query("DELETE FROM ProductRankMonthly m WHERE m.period = :period")
    fun deleteByPeriod(period: String)

    fun findByPeriodOrderByRankPositionAsc(period: String): List<ProductRankMonthly>
}
