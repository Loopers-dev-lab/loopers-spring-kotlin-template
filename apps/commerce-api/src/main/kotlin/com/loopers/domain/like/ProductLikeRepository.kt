package com.loopers.domain.like

interface ProductLikeRepository {
    fun save(productLike: ProductLike): ProductLike
    fun deleteByUserIdAndProductId(userId: Long, productId: Long): Long
}
