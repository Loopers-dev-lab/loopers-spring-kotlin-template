package com.loopers.domain.product

import com.loopers.support.values.Money

data class ProductView(
    val productId: Long,
    val productName: String,
    val price: Money,
    val status: ProductSaleStatus,
    val brandId: Long,
    val brandName: String,
    val stockQuantity: Int,
    val likeCount: Long,
) {
    companion object {
        fun of(
            product: Product,
            stock: Stock,
            brand: Brand,
            statistic: ProductStatistic,
        ): ProductView {
            return ProductView(
                productId = product.id,
                productName = product.name,
                price = product.price,
                status = product.status,
                brandId = brand.id,
                brandName = brand.name,
                stockQuantity = stock.quantity,
                likeCount = statistic.likeCount,
            )
        }
    }
}
