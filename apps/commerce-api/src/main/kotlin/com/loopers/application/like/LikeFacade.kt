package com.loopers.application.like

import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: ProductLikeService,
    private val productService: ProductService,
) {
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        productService.findProductById(productId)
        val likeResult = likeService.addLike(userId, productId)
        if (likeResult.isChanged) {
            productService.increaseProductLikeCount(productId)
        }
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        productService.findProductById(productId)
        val likeResult = likeService.removeLike(userId, productId)
        if (likeResult.isChanged) {
            productService.decreaseProductLikeCount(productId)
        }
    }
}
