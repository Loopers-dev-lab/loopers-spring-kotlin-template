package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * ProductStatistic 엔티티 - 상품 통계 정보
 *
 * - commerce-api와 동일한 테이블(product_statistics)을 참조
 * - 낙관적 락(@Version)으로 동시성 제어
 */
@Entity
@Table(name = "product_statistics")
class ProductStatistic(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,

    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0,
) : BaseEntity() {
    fun applyLikeChanges(types: List<UpdateLikeCountCommand.LikeType>) {
        val delta = types.sumOf { type ->
            when (type) {
                UpdateLikeCountCommand.LikeType.CREATED -> 1L
                UpdateLikeCountCommand.LikeType.CANCELED -> -1L
            }
        }
        likeCount = maxOf(0, likeCount + delta)
    }

    fun applySalesChanges(quantities: List<Int>) {
        salesCount += quantities.sumOf { it.toLong() }
    }

    fun applyViewChanges(count: Int) {
        viewCount += count
    }
}
