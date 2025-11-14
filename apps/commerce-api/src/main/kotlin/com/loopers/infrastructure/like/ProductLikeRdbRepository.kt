package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import org.springframework.stereotype.Repository

@Repository
class ProductLikeRdbRepository(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun deleteByUserIdAndProductId(userId: Long, productId: Long): Long {
        return productLikeJpaRepository.deleteByUserIdAndProductId(userId, productId)
    }

    override fun upsert(productLike: ProductLike): Int {
        return productLikeJpaRepository.upsertLike(productLike.userId, productLike.productId)
    }
}
