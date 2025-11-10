package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.like.Like
import com.loopers.domain.product.Product
import java.math.BigDecimal
import java.time.ZonedDateTime

data class LikedProductInfo(
    val productId: Long,
    val productName: String,
    val price: BigDecimal,
    val currency: String,
    val brand: BrandInfo,
    val likedAt: ZonedDateTime,
) {
    companion object {
        fun from(
            like: Like,
            product: Product,
        ): LikedProductInfo = LikedProductInfo(
            productId = product.id,
            productName = product.name,
            price = product.price.amount,
            currency = product.price.currency.name,
            brand = BrandInfo.from(product.brand),
            likedAt = like.createdAt,
        )
    }
}
