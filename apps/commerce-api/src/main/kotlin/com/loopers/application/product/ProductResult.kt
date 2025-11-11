package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.ProductLike
import com.loopers.domain.product.Product

object ProductResult {

    data class Info(
        val id: Long,
        val name: String,
        val price: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(
                product: Product,
                productLikes: List<ProductLike>,
                brands: List<Brand>,
            ): Info {
                val brandName = brands.find { it.id == product.brandId }!!.name
                val likeCount = productLikes.count { it.productId == product.id }.toLong()

                return Info(
                    id = product.id,
                    name = product.name,
                    price = product.price,
                    brandName = brandName,
                    likeCount = likeCount,
                )
            }
        }
    }
}
