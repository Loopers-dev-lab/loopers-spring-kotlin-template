package com.loopers.domain.product.dto.result

import com.loopers.domain.brand.dto.result.BrandResult.BrandDetail
import com.loopers.domain.brand.dto.result.BrandResult.BrandDetails
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.dto.result.LikeCountResult.LikeCountDetail
import com.loopers.domain.like.dto.result.LikeCountResult.LikeCountDetails
import com.loopers.domain.product.dto.result.ProductResult.ProductDetail
import com.loopers.domain.product.dto.result.ProductResult.ProductDetails
import com.loopers.domain.product.dto.result.ProductResult.ProductPageDetails

class ProductWithResult {
    data class WithBrandDetail(
            val product: ProductDetail,
            val brand: BrandDetail?,
            val likeCount: LikeCountDetail?,
        ) {
        companion object {
            fun from(productDetail: ProductDetail, brandDetail: BrandDetail?, likeCount: LikeCountDetail?): WithBrandDetail {
                return WithBrandDetail(productDetail, brandDetail, likeCount)
            }
        }
    }

    data class WithBrandDetails(
        val products: List<WithBrandDetail>,
    ) {
        companion object {
            fun from(
                productDetails: ProductDetails,
                brandDetails: BrandDetails,
                likeCountDetails: LikeCountDetails,
            ): WithBrandDetails {
                val brandDetailMap = brandDetails.brands.associateBy { it.id }
                val likeCountDetailMap = likeCountDetails.likeCounts.associateBy { it.targetId }

                val result = productDetails.products.map { productDetail ->
                    val brandDetail = brandDetailMap[productDetail.brandId]
                    val likeCountDetail = likeCountDetailMap[productDetail.id]

                    WithBrandDetail.from(productDetail, brandDetail, likeCountDetail)
                }

                return WithBrandDetails(result)
            }
        }
    }

    data class PageWithBrandDetails(
        val products: PageResult<WithBrandDetail>,
    ) {
        companion object {
            fun from(
                productPageDetails: ProductPageDetails,
                brandDetails: BrandDetails,
                likeCountDetails: LikeCountDetails,
            ): PageWithBrandDetails {
                val withBrandDetails = WithBrandDetails.from(
                    productPageDetails.products.data.let { ProductDetails(it) },
                    brandDetails,
                    likeCountDetails,
                )

                return PageWithBrandDetails(
                    PageResult(withBrandDetails.products, productPageDetails.products.page),
                )
            }
        }
    }
}
