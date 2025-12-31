package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * ProductDailyMetric 엔티티 - 일별 상품 행동 집계
 *
 * - 1일 단위로 상품별 조회/좋아요/주문금액 통계를 저장
 * - (statDate, productId) 조합이 고유
 * - viewCount는 음수가 될 수 없음
 * - likeCount는 음수 가능 (예: 특정 날에 좋아요 취소가 더 많으면 음수)
 * - 시간별 집계를 롤업하여 일별 집계 생성
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

    /**
     * Convert entity to CountSnapshot for score calculation
     */
    fun toSnapshot(): CountSnapshot =
        CountSnapshot(
            views = viewCount,
            likes = likeCount,
            orderAmount = orderAmount,
        )

    companion object {
        fun create(
            statDate: LocalDate,
            productId: Long,
            viewCount: Long = 0,
            likeCount: Long = 0,
            orderAmount: BigDecimal = BigDecimal.ZERO,
        ): ProductDailyMetric {
            return ProductDailyMetric(
                statDate = statDate,
                productId = productId,
                viewCount = viewCount,
                likeCount = likeCount,
                orderAmount = orderAmount,
            )
        }
    }
}
