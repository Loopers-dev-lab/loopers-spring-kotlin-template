package com.loopers.domain.ranking

import java.time.LocalDate

interface ProductRankDailyRepository {
    fun save(entity: ProductRankDaily): ProductRankDaily
    fun saveAll(entities: List<ProductRankDaily>): List<ProductRankDaily>
    fun deleteByRankingDate(date: LocalDate)
    fun findByRankingDateBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ProductRankDaily>

    fun findByRankingDate(date: LocalDate): List<ProductRankDaily>
}
