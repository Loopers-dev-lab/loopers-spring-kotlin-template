package com.loopers.domain.ranking

interface ProductRankMonthlyRepository {
    fun findByYearMonth(yearMonth: String): List<ProductRankMonthly>

    fun findTopByYearMonthOrderByRank(
        yearMonth: String,
        limit: Int,
    ): List<ProductRankMonthly>

    fun saveAll(ranks: List<ProductRankMonthly>): List<ProductRankMonthly>

    fun deleteByYearMonth(yearMonth: String)
}
