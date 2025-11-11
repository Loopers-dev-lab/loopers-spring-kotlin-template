package com.loopers.domain.like

import org.springframework.stereotype.Service

@Service
class ProductLikeService(
    private val productLikeRepository: ProductLikeRepository,
) {
    fun findAllBy(productIds: List<Long>): List<ProductLike> = productLikeRepository.findAllBy(productIds)

    fun findAllBy(productId: Long): List<ProductLike> = productLikeRepository.findAllBy(productId)
}
