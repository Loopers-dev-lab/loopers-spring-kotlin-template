package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductMonthlyRanking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.YearMonth

/**
 * 월간 상품 랭킹 JPA Repository
 */
interface ProductMonthlyRankingJpaRepository : JpaRepository<ProductMonthlyRanking, Long> {
    fun findByMonthPeriod(monthPeriod: YearMonth): List<ProductMonthlyRanking>

    @Modifying
    @Query(
        """
        DELETE FROM ProductMonthlyRanking m
        WHERE m.monthPeriod = :monthPeriod
    """,
    )
    fun deleteByMonthPeriod(
        @Param("monthPeriod") monthPeriod: YearMonth,
    )
}
