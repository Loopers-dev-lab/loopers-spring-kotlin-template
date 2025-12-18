package com.loopers.application.product

import com.loopers.domain.product.ProductSaleStatus
import com.loopers.domain.product.ProductView
import com.loopers.support.values.Money

/**
 * Cache DTO for product detail
 *
 * Version-managed class for cache serialization/deserialization contract.
 * When fields change, create V2 and update cache key version accordingly.
 *
 * Related cache key: ProductCacheKeys.ProductDetail (product-detail:v1:{productId})
 */
data class CachedProductDetailV1(
    val productId: Long,
    val productName: String,
    val price: Long,
    val status: String,
    val brandId: Long,
    val brandName: String,
    val stockQuantity: Int,
    val likeCount: Long,
) {
    companion object {
        fun from(view: ProductView): CachedProductDetailV1 {
            return CachedProductDetailV1(
                productId = view.productId,
                productName = view.productName,
                price = view.price.amount.toLong(),
                status = view.status.name,
                brandId = view.brandId,
                brandName = view.brandName,
                stockQuantity = view.stockQuantity,
                likeCount = view.likeCount,
            )
        }
    }

    fun toProductView(): ProductView {
        return ProductView(
            productId = productId,
            productName = productName,
            price = Money.krw(price),
            status = ProductSaleStatus.valueOf(status),
            brandId = brandId,
            brandName = brandName,
            stockQuantity = stockQuantity,
            likeCount = likeCount,
        )
    }
}
