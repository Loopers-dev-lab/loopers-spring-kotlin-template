package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import com.loopers.domain.member.Member
import com.loopers.domain.product.Product
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
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
    member: Member,
    product: Product,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member = member
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product = product
        protected set

    companion object {
        fun of(member: Member, product: Product): Like {
            return Like(member, product)
        }
    }
}
