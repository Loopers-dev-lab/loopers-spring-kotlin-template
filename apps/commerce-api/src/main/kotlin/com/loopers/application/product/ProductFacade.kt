package com.loopers.application.product

import com.loopers.domain.like.LikeQueryService
import com.loopers.domain.product.ProductQueryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productQueryService: ProductQueryService,
    private val likeQueryService: LikeQueryService,
) {
    fun getProducts(brandId: Long?, sort: String, pageable: Pageable): Page<ProductListInfo> {
        val products = productQueryService.findProducts(brandId, sort, pageable)
        val productIds = products.content.map { it.id }
        val likeCountMap = likeQueryService.countByProductIdIn(productIds)

        return products.map { product ->
            val likeCount = likeCountMap[product.id] ?: 0L
            ProductListInfo.from(product, likeCount)
        }
    }

    fun getProductDetail(productId: Long): ProductDetailInfo {
        val productDetail = productQueryService.getProductDetail(productId)
        val likeCount = likeQueryService.countByProductId(productId)
        return ProductDetailInfo.from(productDetail.product, productDetail.stock, likeCount)
    }

    fun getLikedProducts(userId: Long, pageable: Pageable): Page<LikedProductInfo> {
        val likes = likeQueryService.getLikesByUserId(userId, pageable)
        val productIds = likes.content.map { it.productId }
        val products = productQueryService.getProductsByIds(productIds)
        val productMap = products.associateBy { it.id }

        return likes.map { like ->
            val product = productMap[like.productId]
                ?: throw IllegalStateException("Product not found: ${like.productId}")
            LikedProductInfo.from(like, product)
        }
    }
}
