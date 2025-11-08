package com.loopers.domain.product

import com.loopers.domain.like.LikeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

data class ProductWithLikeCount(
    val product: Product,
    val likeCount: Long,
)

@Service
class ProductQueryService(
    private val productRepository: ProductRepository,
    private val likeRepository: LikeRepository,
) {
    fun findProducts(brandId: Long?, sort: String, pageable: Pageable): Page<ProductWithLikeCount> {
        val products = productRepository.findAll(brandId, sort, pageable)

        return products.map { product ->
            val likeCount = likeRepository.countByProductId(product.id)
            ProductWithLikeCount(product, likeCount)
        }
    }
}
