package com.loopers.domain.like

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

data class LikedProductData(
    val like: Like,
    val product: Product,
)

@Service
class LikeQueryService(
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
) {
    fun getLikedProducts(userId: Long, pageable: Pageable): Page<LikedProductData> {
        val likes = likeRepository.findByUserId(userId, pageable)
        return likes.map { like ->
            val product = productRepository.findById(like.productId)!!
            LikedProductData(like, product)
        }
    }
}
