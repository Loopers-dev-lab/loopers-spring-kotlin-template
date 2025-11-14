package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_likes")
class ProductLike(
    val productId: Long,
    val userId: Long,
) : BaseEntity() {
    companion object {
        fun create(productId: Long, userId: Long): ProductLike {
            return ProductLike(productId, userId)
        }

        fun of(productId: Long, userId: Long): ProductLike {
            return ProductLike(productId, userId)
        }
    }
}
