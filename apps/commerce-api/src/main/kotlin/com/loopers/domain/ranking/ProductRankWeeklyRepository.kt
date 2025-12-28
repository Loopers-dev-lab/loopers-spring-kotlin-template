package com.loopers.domain.ranking

interface ProductRankWeeklyRepository {
    fun findByYearWeek(yearWeek: String): List<ProductRankWeekly>

    fun findTopByYearWeekOrderByRank(
        yearWeek: String,
        limit: Int,
    ): List<ProductRankWeekly>

    fun saveAll(ranks: List<ProductRankWeekly>): List<ProductRankWeekly>

    fun deleteByYearWeek(yearWeek: String)
}
