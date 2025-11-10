package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productRepository: ProductRepository,
) {
    fun addLike(userId: Long, productId: Long) {
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        likeService.addLike(userId, productId)
    }

    fun removeLike(userId: Long, productId: Long) {
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        likeService.removeLike(userId, productId)
    }
}
