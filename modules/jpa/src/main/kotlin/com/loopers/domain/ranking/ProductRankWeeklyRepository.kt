package com.loopers.domain.ranking

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRankWeeklyRepository {
    fun save(entity: ProductRankWeekly): ProductRankWeekly
    fun saveAll(entities: List<ProductRankWeekly>): List<ProductRankWeekly>
    fun deleteAll()
    fun deleteByYearWeek(yearWeek: String)
    fun findByYearWeek(yearWeek: String): List<ProductRankWeekly>
    fun findByYearWeekOrderByRankAsc(yearWeek: String, pageable: Pageable): Page<ProductRankWeekly>
    fun findTopByYearWeekOrderByRank(
        yearWeek: String,
        limit: Int,
    ): List<ProductRankWeekly>
}
