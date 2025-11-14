package com.loopers.application.like

import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import org.springframework.transaction.annotation.Transactional

open class LikeFacade(
    private val likeService: ProductLikeService,
    private val productService: ProductService,
) {
    @Transactional
    open fun addLike(userId: Long, productId: Long) {
        val likeResult = likeService.addLike(userId, productId)
        if (likeResult.isChanged) {
            productService.increaseProductLikeCount(productId)
        }
    }

    @Transactional
    open fun removeLike(userId: Long, productId: Long) {
        val likeResult = likeService.removeLike(userId, productId)
        if (likeResult.isChanged) {
            productService.decreaseProductLikeCount(productId)
        }
    }
}
