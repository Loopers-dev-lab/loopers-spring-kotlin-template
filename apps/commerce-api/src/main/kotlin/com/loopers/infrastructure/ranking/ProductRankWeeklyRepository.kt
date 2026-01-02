package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankWeekly
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankWeeklyRepository : JpaRepository<ProductRankWeekly, Long> {
    fun findByYearWeekOrderByRankPositionAsc(yearWeek: String): List<ProductRankWeekly>
}
