package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * ProductDailyMetric 엔티티 - 일별 상품 행동 집계 (읽기 전용)
 *
 * commerce-batch 모듈에서 ProductDailyMetric 테이블을 읽기 위한 엔티티
 * - commerce-streamer에서 원본 관리
 * - 배치에서는 읽기 전용으로 사용
 */
@Entity
@Table(
    name = "product_daily_metric",
    indexes = [
        Index(name = "uk_stat_date_product", columnList = "stat_date, product_id", unique = true),
    ],
)
class ProductDailyMetric(
    @Column(name = "stat_date", nullable = false)
    val statDate: LocalDate,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "view_count", nullable = false)
    val viewCount: Long = 0,

    @Column(name = "like_count", nullable = false)
    val likeCount: Long = 0,

    @Column(name = "order_amount", nullable = false, precision = 15, scale = 2)
    val orderAmount: BigDecimal = BigDecimal.ZERO,
) : BaseEntity()
