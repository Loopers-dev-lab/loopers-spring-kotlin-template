package com.loopers.infrastructure

import com.loopers.domain.ranking.ProductRankWeekly
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductRankWeeklyRepository : JpaRepository<ProductRankWeekly, Long> {
    @Modifying
    @Query("DELETE FROM ProductRankWeekly m WHERE m.yearWeek = :yearWeek")
    fun deleteByYearWeek(yearWeek: String)

    fun findByYearWeekOrderByRankPositionAsc(yearWeek: String): List<ProductRankWeekly>
}
