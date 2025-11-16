package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        likeService.addLike(userId, productId)
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        likeService.removeLike(userId, productId)
    }
}
