package com.loopers.application.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.product.ProductModel
import java.math.BigDecimal

data class ProductInfo(
    val id: Long,
    val name: String,
    val stock: Long,
    val price: BigDecimal,
    val likeCount: Long,
    val brandId: Long,
    val brandName: String,
) {
    companion object {
        fun from(product: ProductModel, brand: BrandModel, likeCount: Long, stockAmount: Long): ProductInfo = ProductInfo(
            product.id,
            product.name,
            stockAmount,
            product.price.amount,
            likeCount,
            brand.id,
            brand.name,
        )
    }
}
