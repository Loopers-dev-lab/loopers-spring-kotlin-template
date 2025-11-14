package com.loopers.domain.like

interface LikeRepository {

    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeModel?

    fun save(likeModel: LikeModel): LikeModel

    fun countByProductId(productId: Long): Long

    fun deleteByUserIdAndProductId(userId: Long, productId: Long)
}
