package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo

class ProductV1Response {
    data class GetProducts(
        val products: List<ProductDto>,
        val hasNext: Boolean,
    ) {
        companion object {
            fun from(info: ProductInfo.FindProducts): GetProducts {
                return GetProducts(
                    products = info.products.map { ProductDto.from(it) },
                    hasNext = info.hasNext,
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
                    product = ProductDto.from(info),
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
            fun from(info: ProductInfo.FindProductById): ProductDto {
                return ProductDto(
                    id = info.productId,
                    name = info.name,
                    price = info.price.amount.toInt(),
                    stock = info.stock.amount,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    likeCount = info.likeCount,
                )
            }

            fun from(unit: ProductInfo.FindProductsUnit): ProductDto {
                return ProductDto(
                    id = unit.productId,
                    name = unit.name,
                    price = unit.price.amount.toInt(),
                    stock = unit.stock.amount,
                    brandId = unit.brandId,
                    brandName = unit.brandName,
                    likeCount = unit.likeCount,
                )
            }
        }
    }
}
