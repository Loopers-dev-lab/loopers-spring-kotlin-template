package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeModel
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<LikeModel, Long> {

    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeModel?

    fun save(likeModel: LikeModel): LikeModel

    fun countByProductId(productId: Long): Long

    fun deleteByUserIdAndProductId(userId: Long, productId: Long)
}
