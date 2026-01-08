package com.loopers.infrastructure.ranking

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * MvProductRankWeekly 조회 전용 엔티티 - 주간 상품 인기 랭킹
 *
 * 이 엔티티는 commerce-api에서 조회 전용으로 사용됩니다.
 * 실제 데이터 생성/수정은 commerce-batch에서 수행됩니다.
 */
@Entity
@Table(
    name = "mv_product_rank_weekly",
    indexes = [
        Index(name = "uk_weekly_rank", columnList = "base_date, `rank`", unique = true),
    ],
)
class MvProductRankWeekly(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "base_date", nullable = false)
    val baseDate: LocalDate,

    @Column(name = "`rank`", nullable = false)
    val rank: Int,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "score", nullable = false, precision = 15, scale = 2)
    val score: BigDecimal,
)
