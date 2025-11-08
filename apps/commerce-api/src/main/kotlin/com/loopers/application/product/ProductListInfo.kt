package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.product.Product
import java.math.BigDecimal

data class ProductListInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val currency: String,
    val brand: BrandInfo,
    val likeCount: Long
) {
    companion object {
        fun from(product: Product, likeCount: Long): ProductListInfo {
            return ProductListInfo(
                id = product.id,
                name = product.name,
                price = product.price.amount,
                currency = product.price.currency.name,
                brand = BrandInfo.from(product.brand),
                likeCount = likeCount
            )
        }
    }
}
