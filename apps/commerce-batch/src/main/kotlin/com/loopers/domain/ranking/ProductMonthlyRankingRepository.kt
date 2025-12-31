package com.loopers.domain.ranking

interface ProductMonthlyRankingRepository {
    fun saveAll(monthlyRankings: List<ProductMonthlyRanking>): List<ProductMonthlyRanking>

    fun findByMonthPeriod(monthPeriod: java.time.YearMonth): List<ProductMonthlyRanking>

    fun deleteByMonthPeriod(monthPeriod: java.time.YearMonth)
}
