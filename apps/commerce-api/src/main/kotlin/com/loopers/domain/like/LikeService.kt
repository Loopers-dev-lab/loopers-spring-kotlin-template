package com.loopers.domain.like

import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
    private val productService: ProductService,
) {

    @Transactional(readOnly = true)
    fun countLikesByProductId(productId: Long): Long {
        return likeRepository.countByProductId(productId)
    }

    @Transactional
    fun addLike(userId: Long, productId: Long) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        likeRepository.save(Like.of(userId, productId))

        val product = productService.getProduct(productId)
        product.incrementLikeCount()
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        val like = likeRepository.findByUserIdAndProductId(userId, productId) ?: return
        likeRepository.delete(like)

        val product = productService.getProduct(productId)
        product.decrementLikeCount()
    }
}
