package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productService: ProductService,
) {

    @Transactional
    fun addLike(userId: Long, productId: Long) {
        productService.getProduct(productId)
        likeService.addLike(userId, productId)
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        likeService.removeLike(userId, productId)
    }
}
