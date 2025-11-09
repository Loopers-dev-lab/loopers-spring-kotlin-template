package com.loopers.application.like

import com.loopers.domain.like.LikeQueryService
import com.loopers.domain.like.LikeService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val likeQueryService: LikeQueryService,
) {
    fun addLike(userId: Long, productId: Long) {
        likeService.addLike(userId, productId)
    }

    fun removeLike(userId: Long, productId: Long) {
        likeService.removeLike(userId, productId)
    }

    fun getLikedProducts(userId: Long, pageable: Pageable): Page<LikedProductInfo> {
        val likedProducts = likeQueryService.getLikedProducts(userId, pageable)
        return likedProducts.map { LikedProductInfo.from(it.like, it.product) }
    }
}
