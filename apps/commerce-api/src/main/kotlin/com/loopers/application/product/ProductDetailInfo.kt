package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import java.math.BigDecimal

data class ProductDetailInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val currency: String,
    val brand: BrandInfo,
    val stockQuantity: Int,
    val likeCount: Long,
) {
    companion object {
        fun from(
            product: Product,
            stock: Stock,
            likeCount: Long,
        ): ProductDetailInfo = ProductDetailInfo(
            id = product.id,
            name = product.name,
            price = product.price.amount,
            currency = product.price.currency.name,
            brand = BrandInfo.from(product.brand),
            stockQuantity = stock.quantity,
            likeCount = likeCount,
        )
    }
}
