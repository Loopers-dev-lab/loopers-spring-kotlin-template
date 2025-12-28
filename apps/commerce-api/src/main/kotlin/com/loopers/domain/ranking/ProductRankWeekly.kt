package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 주간 랭킹 집계 테이블 (TOP 100)
 * 매주 일요일 01:00에 배치로 집계
 */
@Entity
@Table(
    name = "mv_product_rank_weekly",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_rank_weekly_year_week_product",
            columnNames = ["`year_week`", "product_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_product_rank_weekly_year_week", columnList = "`year_week` DESC"),
        Index(name = "idx_product_rank_weekly_year_week_rank", columnList = "`year_week`, `rank`"),
    ],
)
class ProductRankWeekly(
    /**
     * 연도-주차 (yyyy'W'ww, 예: 2025W01)
     */
    @Column(name = "`year_week`", nullable = false, length = 7)
    val yearWeek: String,

    /**
     * 상품 ID
     */
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    /**
     * 집계 점수 (주간 평균)
     */
    @Column(name = "score", nullable = false)
    val score: Double,

    /**
     * 주간 순위 (1부터 시작)
     */
    @Column(name = "`rank`", nullable = false)
    var rank: Int,

    /**
     * 집계 시작일 (주의 월요일)
     */
    @Column(name = "period_start", nullable = false)
    val periodStart: LocalDate,

    /**
     * 집계 종료일 (주의 일요일)
     */
    @Column(name = "period_end", nullable = false)
    val periodEnd: LocalDate,
) : BaseEntity() {

    companion object {
        fun yearWeekToString(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy'W'ww"))
    }
}
