package com.loopers.interfaces.api.product.response

import com.loopers.domain.brand.dto.result.BrandResult.BrandDetail
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.dto.result.LikeCountResult.LikeCountDetail
import com.loopers.domain.product.dto.result.ProductResult.ProductDetail
import com.loopers.domain.product.dto.result.ProductWithResult

class ProductV1Response {
    data class ProductResponse(
        val product: ProductDetail,
        val brand: BrandDetail?,
        val likeCount: LikeCountDetail?,
    ) {
        companion object {
            fun from(product: ProductWithResult.WithBrandDetail): ProductResponse {
                return product
                    .let {
                        ProductResponse(
                            product = it.product,
                            brand = it.brand,
                            likeCount = it.likeCount,
                        )
                    }
            }
        }
    }

    data class ProductsResponse(
        val products: PageResult<ProductWithResult.WithBrandDetail>,
    ) {
        companion object {
            fun from(product: ProductWithResult.PageWithBrandDetails): ProductsResponse {
                return ProductsResponse(
                    products = product.products,
                )
            }
        }
    }
}
