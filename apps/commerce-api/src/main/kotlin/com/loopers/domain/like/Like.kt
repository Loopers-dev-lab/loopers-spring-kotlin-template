package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "loopers_like",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_like_user_product",
            columnNames = ["user_id", "product_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_like_product_id", columnList = "product_id"),
    ],
)
class Like(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val productId: Long,
) : BaseEntity() {

    companion object {
        fun of(userId: Long, productId: Long): Like {
            return Like(
                userId = userId,
                productId = productId,
            )
        }
    }
}
