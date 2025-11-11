package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class ProductLikeRepositoryImpl(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun findAllBy(productIds: List<Long>): List<ProductLike> =
        productLikeJpaRepository.findAllByProductIdIn(productIds)

    override fun findAllBy(productId: Long): List<ProductLike> =
        productLikeJpaRepository.findAllByProductId(productId)

    override fun findAllBy(
        userId: Long,
        pageable: Pageable,
    ): Page<ProductLike> =
        productLikeJpaRepository.findAllByUserId(userId, pageable)

    override fun findBy(
        productId: Long,
        userId: Long,
    ): ProductLike? =
        productLikeJpaRepository.findBy(productId, userId)

    override fun save(productLike: ProductLike): ProductLike =
        productLikeJpaRepository.save(productLike)

    override fun deleteBy(
        productId: Long,
        userId: Long,
    ) = productLikeJpaRepository.deleteBy(productId, userId)
}
