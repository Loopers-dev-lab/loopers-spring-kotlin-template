package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_statistics")
class ProductStatistic(
    @Column(name = "product_id", nullable = false)
    val productId: Long,
    @Column(name = "like_count", nullable = false)
    val likeCount: Long,
) : BaseEntity() {
    companion object {
        fun create(productId: Long): ProductStatistic {
            return ProductStatistic(productId, 0)
        }

        fun of(productId: Long, likeCount: Long): ProductStatistic {
            return ProductStatistic(productId, likeCount)
        }
    }
}
