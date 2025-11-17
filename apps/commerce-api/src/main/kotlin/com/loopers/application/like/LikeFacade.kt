package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.signal.ProductTotalSignalService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class LikeFacade(private val likeService: LikeService, private val productTotalSignalService: ProductTotalSignalService) {

    @Transactional
    fun like(userId: Long, productId: Long) {
        val isNewLike = likeService.like(userId, productId)
        if (isNewLike) {
            productTotalSignalService.incrementLikeCount(productId)
        }
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        val isDeleted = likeService.unLike(userId, productId)
        if (isDeleted) {
            productTotalSignalService.decrementLikeCount(productId)
        }
    }
}
