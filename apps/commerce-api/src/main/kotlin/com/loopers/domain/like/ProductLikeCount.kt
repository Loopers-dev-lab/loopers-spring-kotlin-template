package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_like_count",
    indexes = [
        Index(name = "idx_product_like_count_product", columnList = "ref_product_id", unique = true),
        Index(name = "idx_product_like_count_count", columnList = "like_count DESC"),
    ],
)
class ProductLikeCount(

    @Column(name = "ref_product_id", nullable = false)
    val productId: Long,

    @Column(name = "like_count", nullable = false)
    val likeCount: Long,

    ) : BaseEntity() {

    companion object {
        fun create(productId: Long, likeCount: Long): ProductLikeCount {
            return ProductLikeCount(
                productId = productId,
                likeCount = likeCount,
            )
        }
    }
}
