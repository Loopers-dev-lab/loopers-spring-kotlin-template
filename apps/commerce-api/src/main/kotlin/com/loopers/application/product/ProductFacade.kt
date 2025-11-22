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

        return products.map { product ->
            ProductListInfo.from(product, product.likeCount)
        }
    }

    fun getProductDetail(productId: Long): ProductDetailInfo {
        val productDetail = productQueryService.getProductDetail(productId)
        return ProductDetailInfo.from(productDetail.product, productDetail.stock, productDetail.product.likeCount)
    }

    fun getLikedProducts(userId: Long, pageable: Pageable): Page<LikedProductInfo> {
        val validLikes = likeQueryService.getValidLikesByUserId(userId, pageable)
        val productIds = validLikes.content.map { it.productId }
        val products = productQueryService.getProductsByIds(productIds)
        val productMap = products.associateBy { it.id }

        val likedProducts = validLikes.content.mapNotNull { like ->
            productMap[like.productId]?.let { product ->
                LikedProductInfo.from(like, product)
            }
        }

        return org.springframework.data.domain.PageImpl(
            likedProducts,
            validLikes.pageable,
            validLikes.totalElements,
        )
    }
}
