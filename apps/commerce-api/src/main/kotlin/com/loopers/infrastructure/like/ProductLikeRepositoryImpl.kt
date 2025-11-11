package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import org.springframework.stereotype.Repository

@Repository
class ProductLikeRepositoryImpl(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun findAllBy(productIds: List<Long>): List<ProductLike> = productLikeJpaRepository.findAllByProductIdIn(productIds)
    override fun findAllBy(productId: Long): List<ProductLike> = productLikeJpaRepository.findAllByProductId(productId)
}
