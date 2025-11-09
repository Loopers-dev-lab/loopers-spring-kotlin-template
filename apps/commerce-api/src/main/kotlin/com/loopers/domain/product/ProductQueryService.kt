package com.loopers.domain.product

import com.loopers.domain.like.LikeRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

data class ProductWithLikeCount(
    val product: Product,
    val likeCount: Long,
)

data class ProductDetailData(
    val product: Product,
    val stock: Stock,
    val likeCount: Long,
)

@Service
class ProductQueryService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val likeRepository: LikeRepository,
) {
    fun findProducts(brandId: Long?, sort: String, pageable: Pageable): Page<ProductWithLikeCount> {
        val products = productRepository.findAll(brandId, sort, pageable)
        val productIds = products.content.map { it.id }
        val likeCountMap = likeRepository.countByProductIdIn(productIds)

        return products.map { product ->
            val likeCount = likeCountMap[product.id] ?: 0L
            ProductWithLikeCount(product, likeCount)
        }
    }

    fun getProductDetail(productId: Long): ProductDetailData {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        val stock = stockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")

        val likeCount = likeRepository.countByProductId(productId)

        return ProductDetailData(product, stock, likeCount)
    }
}
