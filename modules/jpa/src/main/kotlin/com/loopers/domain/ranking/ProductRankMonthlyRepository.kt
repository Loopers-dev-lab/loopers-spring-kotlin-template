package com.loopers.domain.ranking

interface ProductRankMonthlyRepository {
    fun save(entity: ProductRankMonthly): ProductRankMonthly
    fun saveAll(entities: List<ProductRankMonthly>): List<ProductRankMonthly>
    fun deleteByYearMonth(yearMonth: String)
    fun findByYearMonth(yearMonth: String): List<ProductRankMonthly>
    fun findTopByYearMonthOrderByRank(
        yearMonth: String,
        limit: Int,
    ): List<ProductRankMonthly>
}
