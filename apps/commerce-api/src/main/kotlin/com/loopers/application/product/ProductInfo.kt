package com.loopers.application.product

import com.loopers.domain.product.Product
import com.loopers.domain.shared.Money
import org.springframework.data.domain.Page

data class ProductInfo(
    val id: Long,
    val name: String,
    val description: String?,
    val price: Money,
    val stock: Int,
    val likesCount: Int,
) {
    companion object {
        fun from(product: Product): ProductInfo {
            return ProductInfo(
                id = product.id,
                name = product.name,
                description = product.description,
                price = product.price,
                stock = product.stock.quantity,
                likesCount = product.likesCount,
            )
        }

        fun fromPage(page: Page<Product>): Page<ProductInfo> {
            return page.map { from(it) }
        }
    }
}
