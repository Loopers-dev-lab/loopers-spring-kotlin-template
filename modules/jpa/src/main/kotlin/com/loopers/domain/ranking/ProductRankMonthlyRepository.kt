package com.loopers.domain.ranking

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRankMonthlyRepository {
    fun save(entity: ProductRankMonthly): ProductRankMonthly
    fun saveAll(entities: List<ProductRankMonthly>): List<ProductRankMonthly>
    fun deleteAll()
    fun deleteByYearMonth(yearMonth: String)
    fun findByYearMonth(yearMonth: String): List<ProductRankMonthly>
    fun findByYearMonthOrderByRankAsc(yearMonth: String, pageable: Pageable): Page<ProductRankMonthly>
    fun findTopByYearMonthOrderByRank(
        yearMonth: String,
        limit: Int,
    ): List<ProductRankMonthly>
}
