package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 일간 랭킹 영구 저장 테이블
 * 매일 23:55에 Redis 데이터를 스냅샷하여 저장
 */
@Entity
@Table(
    name = "product_rank_daily",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_rank_daily_date_product",
            columnNames = ["ranking_date", "product_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_product_rank_daily_date", columnList = "ranking_date DESC"),
        Index(name = "idx_product_rank_daily_date_rank", columnList = "ranking_date, `rank`"),
        Index(name = "idx_product_rank_daily_product_id", columnList = "product_id"),
    ],
)
class ProductRankDaily(
    /**
     * 랭킹 날짜 (해당 일자의 랭킹)
     */
    @Column(name = "ranking_date", nullable = false)
    val rankingDate: LocalDate,

    /**
     * 상품 ID
     */
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    /**
     * 랭킹 점수
     */
    @Column(name = "score", nullable = false)
    val score: Double,

    /**
     * 순위 (1부터 시작)
     */
    @Column(name = "`rank`", nullable = false)
    val rank: Int,

    /**
     * 좋아요 수 (스냅샷)
     */
    @Column(name = "like_count", nullable = false)
    val likeCount: Long = 0,

    /**
     * 조회 수 (스냅샷)
     */
    @Column(name = "view_count", nullable = false)
    val viewCount: Long = 0,

    /**
     * 판매량 (스냅샷)
     */
    @Column(name = "sales_count", nullable = false)
    val salesCount: Long = 0,
) : BaseEntity()
