package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import java.math.BigDecimal
import java.time.ZonedDateTime

class ProductResult {

    data class ProductInfo(
        val id: Long,
        val name: String,
        val price: BigDecimal,
        val brand: BrandInfo,
        val likeCount: Long,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun of(product: Product, brand: Brand, likeCount: Long = 0): ProductInfo {
                return ProductInfo(
                    id = product.id,
                    name = product.name,
                    price = product.price,
                    brand = BrandInfo(
                        id = brand.id,
                        name = brand.name,
                    ),
                    likeCount = likeCount,
                    createdAt = product.createdAt,
                    updatedAt = product.updatedAt,
                )
            }
        }

        data class BrandInfo(
            val id: Long,
            val name: String,
        )
    }
}
