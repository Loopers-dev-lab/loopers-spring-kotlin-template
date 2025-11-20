package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo
import com.loopers.domain.product.ProductView

class ProductV1Response {
    data class GetProducts(
        val products: List<ProductDto>,
        val hasNext: Boolean,
    ) {
        companion object {
            fun from(info: ProductInfo.FindProducts): GetProducts {
                return GetProducts(
                    products = info.products.content.map { ProductDto.from(it) },
                    hasNext = info.products.hasNext(),
                )
            }
        }
    }

    data class GetProduct(
        val product: ProductDto,
    ) {
        companion object {
            fun from(info: ProductInfo.FindProductById): GetProduct {
                return GetProduct(
                    product = ProductDto.from(info.product),
                )
            }
        }
    }

    data class ProductDto(
        val id: Long,
        val name: String,
        val price: Int,
        val stock: Int,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(view: ProductView): ProductDto {
                return ProductDto(
                    id = view.product.id,
                    name = view.product.name,
                    price = view.product.price.amount.toInt(),
                    stock = view.product.stock.amount,
                    brandId = view.brand.id,
                    brandName = view.brand.name,
                    likeCount = view.statistic.likeCount,
                )
            }
        }
    }
}
