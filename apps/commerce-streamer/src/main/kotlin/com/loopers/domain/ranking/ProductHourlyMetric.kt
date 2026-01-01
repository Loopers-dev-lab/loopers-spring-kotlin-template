package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * ProductHourlyMetric 엔티티 - 시간별 상품 행동 집계
 *
 * - 1시간 단위로 상품별 조회/좋아요/주문 통계를 저장
 * - (statHour, productId) 조합이 고유
 * - viewCount는 음수가 될 수 없음
 * - likeCount는 음수 가능 (예: 14:00에 좋아요 → 15:00에 취소 → 15:00 버킷의 likeCount = -1)
 */
@Entity
@Table(
    name = "product_hourly_metric",
    indexes = [
        Index(name = "uk_stat_hour_product", columnList = "stat_hour, product_id", unique = true),
    ],
)
class ProductHourlyMetric(
    @Column(name = "stat_hour", nullable = false)
    val statHour: Instant,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "order_amount", nullable = false, precision = 15, scale = 2)
    var orderAmount: BigDecimal = BigDecimal.ZERO,
) : BaseEntity() {

    init {
        validateCounts()
    }

    private fun validateCounts() {
        require(viewCount >= 0) { "viewCount는 음수가 될 수 없습니다: $viewCount" }
    }

    companion object {
        fun create(
            statHour: Instant,
            productId: Long,
            viewCount: Long = 0,
            likeCount: Long = 0,
            orderAmount: BigDecimal = BigDecimal.ZERO,
        ): ProductHourlyMetric {
            return ProductHourlyMetric(
                statHour = statHour,
                productId = productId,
                viewCount = viewCount,
                likeCount = likeCount,
                orderAmount = orderAmount,
            )
        }
    }
}
