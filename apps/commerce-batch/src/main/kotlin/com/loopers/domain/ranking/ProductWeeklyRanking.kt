package com.loopers.domain.ranking

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 주간 상품 랭킹 집계 테이블
 *
 * 일자별 product_metrics를 주 단위로 집계하여 상품별 랭킹 점수 저장
 */
@Entity
@Table(
    name = "mv_product_rank_weekly",
    indexes = [
        Index(name = "idx_week", columnList = "week_start, week_end"),
        Index(name = "idx_score", columnList = "score"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_weekly_rank", columnNames = ["product_id", "week_start", "week_end"]),
    ],
)
class ProductWeeklyRanking(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "ranking", nullable = false)
    val ranking: Int = 0,

    @Column(name = "product_id", nullable = false)
    val productId: Long = 0,

    @Column(name = "score", nullable = false)
    val score: Double = 0.0,

    @Column(name = "week_start", nullable = false)
    val weekStart: LocalDate,

    @Column(name = "week_end", nullable = false)
    val weekEnd: LocalDate,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now(),
) {
    companion object {
        fun create(
            ranking: Int,
            productId: Long,
            score: Double,
            weekStart: LocalDate,
            weekEnd: LocalDate,
        ): ProductWeeklyRanking {
            return ProductWeeklyRanking(
                ranking = ranking,
                productId = productId,
                score = score,
                weekStart = weekStart,
                weekEnd = weekEnd,
            )
        }
    }
}
