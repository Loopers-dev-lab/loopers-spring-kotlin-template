package com.loopers.domain.like.dto.result

import com.loopers.domain.brand.dto.result.BrandResult.BrandDetail
import com.loopers.domain.brand.dto.result.BrandResult.BrandDetails
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.dto.result.LikeCountResult.LikeCountDetail
import com.loopers.domain.like.dto.result.LikeCountResult.LikeCountDetails
import com.loopers.domain.like.dto.result.LikeResult.LikeDetail
import com.loopers.domain.like.dto.result.LikeResult.LikeDetails
import com.loopers.domain.like.dto.result.LikeResult.LikePageDetails
import com.loopers.domain.product.dto.result.ProductResult.ProductDetail
import com.loopers.domain.product.dto.result.ProductResult.ProductDetails

class LikeWithResult {
    data class WithProductDetail(
        val like: LikeDetail,
        val product: ProductDetail?,
        val brand: BrandDetail?,
        val likeCount: LikeCountDetail?,
        ) {
        companion object {
            fun from(
                likeDetail: LikeDetail,
                productDetail: ProductDetail?,
                brandDetail: BrandDetail?,
                likeCount: LikeCountDetail?,
            ): WithProductDetail {
                return WithProductDetail(likeDetail, productDetail, brandDetail, likeCount)
            }
        }
    }

    data class WithProductDetails(
        val likes: List<WithProductDetail>,
    ) {
        companion object {
            fun from(
                likeDetails: LikeDetails,
                productDetails: ProductDetails,
                brandDetails: BrandDetails,
                likeCountDetails: LikeCountDetails,
            ): WithProductDetails {
                val productDetailMap = productDetails.products.associateBy { it.id }
                val brandDetailMap = brandDetails.brands.associateBy { it.id }
                val likeCountDetailMap = likeCountDetails.likeCounts.associateBy { it.targetId }

                val result = likeDetails.likes.map { likeDetail ->
                    val productDetail = productDetailMap[likeDetail.targetId]
                    val brandDetail = brandDetailMap[productDetail?.brandId]
                    val likeCountDetail = likeCountDetailMap[likeDetail.targetId]

                    WithProductDetail.from(likeDetail, productDetail, brandDetail, likeCountDetail)
                }

                return WithProductDetails(result)
            }
        }
    }

    data class PageWithProductDetails(
        val likes: PageResult<WithProductDetail>,
    ) {
        companion object {
            fun from(
                likePageDetails: LikePageDetails,
                productDetails: ProductDetails,
                brandDetails: BrandDetails,
                likeCountDetails: LikeCountDetails,
            ): PageWithProductDetails {
                val withProductDetails = WithProductDetails.from(
                    likePageDetails.likes.data.let { LikeDetails(it) },
                    productDetails,
                    brandDetails,
                    likeCountDetails,
                )

                return PageWithProductDetails(
                    PageResult(withProductDetails.likes, likePageDetails.likes.page),
                )
            }
        }
    }
}
