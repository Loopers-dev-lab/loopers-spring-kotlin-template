package com.loopers.domain.ranking

interface ProductRankWeeklyRepository {
    fun save(entity: ProductRankWeekly): ProductRankWeekly
    fun saveAll(entities: List<ProductRankWeekly>): List<ProductRankWeekly>
    fun deleteByYearWeek(yearWeek: String)
    fun findByYearWeek(yearWeek: String): List<ProductRankWeekly>
    fun findTopByYearWeekOrderByRank(
        yearWeek: String,
        limit: Int,
    ): List<ProductRankWeekly>
}
