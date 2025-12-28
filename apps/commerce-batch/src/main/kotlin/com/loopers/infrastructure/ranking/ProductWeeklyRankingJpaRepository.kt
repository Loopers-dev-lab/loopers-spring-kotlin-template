package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductWeeklyRanking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/**
 * 주간 상품 랭킹 JPA Repository
 */
interface ProductWeeklyRankingJpaRepository : JpaRepository<ProductWeeklyRanking, Long> {

    fun findByWeekStartAndWeekEnd(weekStart: LocalDate, weekEnd: LocalDate): List<ProductWeeklyRanking>

    /**
     * 특정 상품의 주차별 랭킹 조회
     */
    fun findByProductIdAndWeekStartAndWeekEnd(
        productId: Long,
        weekStart: LocalDate,
        weekEnd: LocalDate,
    ): List<ProductWeeklyRanking>

    @Query(
        """
        SELECT w
        FROM ProductWeeklyRanking w
        WHERE w.weekStart <= :monthEnd
          AND w.weekEnd >= :monthStart
    """,
    )
    fun findByWeekRange(
        @Param("monthStart") monthStart: LocalDate,
        @Param("monthEnd") monthEnd: LocalDate,
    ): List<ProductWeeklyRanking>

    @Modifying
    @Query(
        """
        DELETE FROM ProductWeeklyRanking w
        WHERE w.weekStart = :weekStart
          AND w.weekEnd = :weekEnd
    """,
    )
    fun deleteByWeekStartAndWeekEnd(
        @Param("weekStart") weekStart: LocalDate,
        @Param("weekEnd") weekEnd: LocalDate,
    )
}
