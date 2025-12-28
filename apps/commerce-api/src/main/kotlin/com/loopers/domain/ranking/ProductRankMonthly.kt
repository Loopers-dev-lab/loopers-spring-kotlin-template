package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.YearMonth

/**
 * 월간 랭킹 집계 테이블 (TOP 100)
 * 매월 1일 02:00에 배치로 집계
 */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_rank_monthly_year_month_product",
            columnNames = ["`year_month`", "product_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_product_rank_monthly_year_month", columnList = "`year_month` DESC"),
        Index(name = "idx_product_rank_monthly_year_month_rank", columnList = "`year_month`, `rank`"),
    ],
)
class ProductRankMonthly(
    /**
     * 연도-월 (yyyyMM, 예: 202501)
     */
    @Column(name = "`year_month`", nullable = false, length = 6)
    val yearMonth: String,

    /**
     * 상품 ID
     */
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    /**
     * 집계 점수 (월간 평균)
     */
    @Column(name = "score", nullable = false)
    val score: Double,

    /**
     * 월간 순위 (1부터 시작)
     */
    @Column(name = "`rank`", nullable = false)
    var rank: Int,

    /**
     * 집계 시작일
     */
    @Column(name = "period_start", nullable = false)
    val periodStart: LocalDate,

    /**
     * 집계 종료일
     */
    @Column(name = "period_end", nullable = false)
    val periodEnd: LocalDate,
) : BaseEntity() {

    companion object {
        fun yearMonthToString(yearMonth: YearMonth): String = yearMonth.toString().replace("-", "")

        fun stringToYearMonth(yearMonth: String): YearMonth {
            require(yearMonth.length == 6) { "Invalid yearMonth format: $yearMonth" }
            return YearMonth.of(
                yearMonth.substring(0, 4).toInt(),
                yearMonth.substring(4, 6).toInt(),
            )
        }
    }
}
