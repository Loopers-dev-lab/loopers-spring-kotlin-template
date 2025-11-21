package com.loopers.interfaces.api.product

import com.loopers.domain.product.ProductResult
import java.math.BigDecimal
import java.time.ZonedDateTime

class ProductResponse {

    data class ProductInfoDto(
        val id: Long,
        val name: String,
        val price: BigDecimal,
        val brand: BrandInfo,
        val likeCount: Long,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(result: ProductResult.ProductInfo): ProductInfoDto {
                return ProductInfoDto(
                    id = result.id,
                    name = result.name,
                    price = result.price,
                    brand = BrandInfo(
                        id = result.brand.id,
                        name = result.brand.name,
                    ),
                    likeCount = result.likeCount,
                    createdAt = result.createdAt,
                    updatedAt = result.updatedAt,
                )
            }
        }

        data class BrandInfo(
            val id: Long,
            val name: String,
        )
    }
}
