package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "likes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["member_id", "product_id"])],
    indexes = [
        Index(name = "idx_likes_member_id", columnList = "member_id"),
        Index(name = "idx_likes_product_id", columnList = "product_id"),
    ],
)
class Like(
    memberId: Long,
    productId: Long,
) : BaseEntity() {

    @Column(name = "member_id", nullable = false)
    var memberId: Long = memberId
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    companion object {
        fun of(memberId: Long, productId: Long): Like {
            return Like(memberId, productId)
        }
    }
}
