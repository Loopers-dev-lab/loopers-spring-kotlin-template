package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo

class ProductV1Dto {
    data class ProductResponse(
        val id: Long,
        val name: String,
        val description: String?,
        val price: Long,
        val stock: Int,
        val brandId: Long,
        val brandName: String,
        val likesCount: Int,
    ) {
        companion object {
            fun from(info: ProductInfo): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    name = info.name,
                    description = info.description,
                    price = info.price.amount,
                    stock = info.stock,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    likesCount = info.likesCount,
                )
            }
        }
    }
}
