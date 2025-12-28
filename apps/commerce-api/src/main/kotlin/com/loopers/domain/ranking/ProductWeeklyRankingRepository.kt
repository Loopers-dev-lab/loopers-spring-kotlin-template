package com.loopers.domain.ranking

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate

interface ProductWeeklyRankingRepository {
    fun findByWeekRange(weekStart: LocalDate, weekEnd: LocalDate, pageable: Pageable): Page<ProductWeeklyRanking>
}
