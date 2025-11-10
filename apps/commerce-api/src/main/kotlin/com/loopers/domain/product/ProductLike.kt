package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_like",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_user",
            columnNames = ["ref_product_id", "ref_user_id"],
        ),
    ],
)
class ProductLike(

    @Column(name = "ref_product_id", nullable = false)
    val productId: Long,

    @Column(name = "ref_user_id", nullable = false)
    val userId: Long,

    ) : BaseEntity() {

    companion object {
        fun create(productId: Long, userId: Long): ProductLike {
            return ProductLike(
                productId = productId,
                userId = userId,
            )
        }
    }
}
