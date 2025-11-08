package com.loopers.application.like

import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository
) {
    fun addLike(userId: Long, productId: Long) {
        likeService.addLike(userId, productId)
    }

    fun removeLike(userId: Long, productId: Long) {
        likeService.removeLike(userId, productId)
    }

    fun getLikedProducts(userId: Long, pageable: Pageable): Page<LikedProductInfo> {
        val likes = likeRepository.findByUserId(userId, pageable)
        return likes.map { like ->
            val product = productRepository.findById(like.productId)!!
            LikedProductInfo.from(like, product)
        }
    }
}
