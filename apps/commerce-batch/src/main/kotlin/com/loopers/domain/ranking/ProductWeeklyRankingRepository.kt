package com.loopers.domain.ranking

import java.time.LocalDate

/**
 * 주간 상품 랭킹 Repository
 */
interface ProductWeeklyRankingRepository {
    fun saveAll(weeklyRankings: List<ProductWeeklyRanking>): List<ProductWeeklyRanking>

    fun findByWeekStartAndWeekEnd(weekStart: LocalDate, weekEnd: LocalDate): List<ProductWeeklyRanking>

    /**
     * Upsert를 위한 특정 상품의 주차별 랭킹 조회
     */
    fun findByProductIdAndWeekStartAndWeekEnd(
        productId: Long,
        weekStart: LocalDate,
        weekEnd: LocalDate,
    ): List<ProductWeeklyRanking>

    fun findByWeekRange(monthStart: LocalDate, monthEnd: LocalDate): List<ProductWeeklyRanking>

    fun deleteByWeekStartAndWeekEnd(weekStart: LocalDate, weekEnd: LocalDate)
}
