package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.signal.ProductTotalSignalService
import org.springframework.stereotype.Component

@Component
class LikeFacade(private val likeService: LikeService, private val productTotalSignalService: ProductTotalSignalService) {

    // TODO : boolean 값을 별로 안 좋아하신다고 하셨는데, 여기서 멱등성을 지키면서 어떻게 코드를 구현할 수 있을까 ?
    fun like(userId: Long, productId: Long) {
        val isNewLike = likeService.like(userId, productId)
        if (isNewLike) {
            productTotalSignalService.incrementLikeCount(productId)
        }
    }

    fun unlike(userId: Long, productId: Long) {
        val isDeleted = likeService.unLike(userId, productId)
        if (isDeleted) {
            productTotalSignalService.decrementLikeCount(productId)
        }
    }
}
