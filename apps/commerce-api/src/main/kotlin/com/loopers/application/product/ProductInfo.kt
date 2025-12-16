package com.loopers.application.product

import com.loopers.domain.product.ProductView
import com.loopers.support.values.Money

class ProductInfo {
    data class FindProductById(
        val productId: Long,
        val name: String,
        val price: Money,
        val stock: Int,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(view: ProductView): FindProductById {
                return FindProductById(
                    productId = view.product.id,
                    name = view.product.name,
                    price = view.product.price,
                    stock = view.stock.quantity,
                    brandId = view.brand.id,
                    brandName = view.brand.name,
                    likeCount = view.statistic.likeCount,
                )
            }
        }
    }

    data class FindProductsUnit(
        val productId: Long,
        val name: String,
        val price: Money,
        val stock: Int,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(view: ProductView): FindProductsUnit {
                return FindProductsUnit(
                    productId = view.product.id,
                    name = view.product.name,
                    price = view.product.price,
                    stock = view.stock.quantity,
                    brandId = view.brand.id,
                    brandName = view.brand.name,
                    likeCount = view.statistic.likeCount,
                )
            }
        }
    }

    data class FindProducts(
        val products: List<FindProductsUnit>,
        val hasNext: Boolean,
    ) {
        companion object {
            fun from(slice: org.springframework.data.domain.Slice<ProductView>): FindProducts {
                return FindProducts(
                    products = slice.content.map { FindProductsUnit.from(it) },
                    hasNext = slice.hasNext(),
                )
            }
        }
    }
}
