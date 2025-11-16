package com.loopers.interfaces.api.like

import com.loopers.application.product.LikedProductInfo
import java.math.BigDecimal
import java.time.ZonedDateTime

class LikeV1Dto {
    data class LikedProductResponse(
        val productId: Long,
        val productName: String,
        val price: BigDecimal,
        val currency: String,
        val brand: BrandSummary,
        val likedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(
                info: LikedProductInfo,
            ): LikedProductResponse = LikedProductResponse(
                productId = info.productId,
                productName = info.productName,
                price = info.price,
                currency = info.currency,
                brand = BrandSummary(
                    id = info.brand.id,
                    name = info.brand.name,
                ),
                likedAt = info.likedAt,
            )
        }
    }

    data class BrandSummary(
        val id: Long,
        val name: String,
    )
}
