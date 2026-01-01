package com.loopers.infrastructure.ranking

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface MvProductRankWeeklyJpaRepository : JpaRepository<MvProductRankWeekly, Long> {

    @Query("SELECT r FROM MvProductRankWeekly r WHERE r.baseDate = :baseDate ORDER BY r.rank ASC")
    fun findByBaseDateOrderByRankAsc(baseDate: LocalDate, pageable: Pageable): List<MvProductRankWeekly>

    @Query("SELECT r FROM MvProductRankWeekly r WHERE r.baseDate = :baseDate AND r.productId = :productId")
    fun findByBaseDateAndProductId(baseDate: LocalDate, productId: Long): MvProductRankWeekly?

    fun existsByBaseDate(baseDate: LocalDate): Boolean
}
