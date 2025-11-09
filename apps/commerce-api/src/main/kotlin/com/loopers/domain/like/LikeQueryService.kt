package com.loopers.domain.like

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
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
        val productIds = likes.content.map { it.productId }
        val products = productRepository.findAllById(productIds)
        val productMap = products.associateBy { it.id }
        val likedProductDataList = likes.content.map { like ->
            val product = productMap[like.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다")
            LikedProductData(like, product)
        }
        return PageableExecutionUtils.getPage(likedProductDataList, pageable) { likes.totalElements }
    }
}
