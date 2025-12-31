package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductWeeklyRanking
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ProductWeeklyRankingJpaRepository : JpaRepository<ProductWeeklyRanking, Long> {
    fun findByWeekStartAndWeekEnd(weekStart: LocalDate, weekEnd: LocalDate, pageable: Pageable): Page<ProductWeeklyRanking>
}
