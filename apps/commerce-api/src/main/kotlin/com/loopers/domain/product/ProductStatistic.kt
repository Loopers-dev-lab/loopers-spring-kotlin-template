package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_statistics",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_statistic_product",
            columnNames = ["product_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_product_statistics_like_count_product_id",
            columnList = "like_count DESC, product_id DESC",
        ),
    ],
)
class ProductStatistic(
    @Column(name = "product_id", nullable = false)
    val productId: Long,
    @Column(name = "like_count", nullable = false)
    val likeCount: Long = 0,
    @Column(name = "sales_count", nullable = false)
    val salesCount: Long = 0,
    @Column(name = "view_count", nullable = false)
    val viewCount: Long = 0,
) : BaseEntity() {
    companion object {
        fun create(productId: Long): ProductStatistic {
            return ProductStatistic(productId, 0, 0, 0)
        }

        fun of(
            productId: Long,
            likeCount: Long,
            salesCount: Long = 0,
            viewCount: Long = 0,
        ): ProductStatistic {
            return ProductStatistic(productId, likeCount, salesCount, viewCount)
        }
    }
}
