package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_likes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_user",
            columnNames = ["product_id", "user_id"],
        ),
    ],
)
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
