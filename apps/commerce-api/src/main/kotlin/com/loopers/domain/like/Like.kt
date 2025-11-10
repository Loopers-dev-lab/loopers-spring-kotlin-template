package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "loopers_like")
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
