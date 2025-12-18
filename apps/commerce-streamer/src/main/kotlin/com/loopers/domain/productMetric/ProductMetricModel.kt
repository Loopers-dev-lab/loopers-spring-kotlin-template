package com.loopers.domain.productMetric

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_metrics")
class ProductMetricModel(
    @Column(name = "ref_product_id", nullable = false)
    var refProductId: Long,
) : BaseEntity() {

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0

    /**
     * likeCount를 delta만큼 업데이트
     * @param delta 변경량 (양수: 증가, 음수: 감소)
     */
    fun updateLikeCount(delta: Long) {
        this.likeCount = (this.likeCount + delta).coerceAtLeast(0)
    }
}
