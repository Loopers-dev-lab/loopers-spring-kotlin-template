package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductDetailInfo
import com.loopers.application.product.ProductListInfo
import java.math.BigDecimal

class ProductV1Dto {
    data class ProductListResponse(
        val id: Long,
        val name: String,
        val price: BigDecimal,
        val currency: String,
        val brand: BrandSummary,
        val likeCount: Long,
    ) {
        companion object {
            fun from(
                info: ProductListInfo,
            ): ProductListResponse = ProductListResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                currency = info.currency,
                brand = BrandSummary(
                    id = info.brand.id,
                    name = info.brand.name,
                ),
                likeCount = info.likeCount,
            )
        }
    }

    data class ProductDetailResponse(
        val id: Long,
        val name: String,
        val price: BigDecimal,
        val currency: String,
        val brand: BrandDetail,
        val stockQuantity: Int,
        val likeCount: Long,
    ) {
        companion object {
            fun from(
                info: ProductDetailInfo,
            ): ProductDetailResponse = ProductDetailResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                currency = info.currency,
                brand = BrandDetail(
                    id = info.brand.id,
                    name = info.brand.name,
                    description = info.brand.description,
                ),
                stockQuantity = info.stockQuantity,
                likeCount = info.likeCount,
            )
        }
    }

    data class BrandSummary(val id: Long, val name: String)

    data class BrandDetail(val id: Long, val name: String, val description: String?)
}
