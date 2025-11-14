package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "likes")
class LikeModel(refUserId: Long, refProductId: Long) : BaseEntity() {

    @Column
    var productId: Long = refProductId

    @Column
    var userId: Long = refUserId

    companion object {
        fun create(userId: Long, productId: Long): LikeModel = LikeModel(userId, productId)
    }
}
