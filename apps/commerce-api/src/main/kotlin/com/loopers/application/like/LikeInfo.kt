package com.loopers.application.like

import com.loopers.application.product.ProductInfo
import com.loopers.domain.like.Like
import com.loopers.domain.product.Product

data class LikeInfo(
    val id: Long,
    val memberId: String,
    val product: ProductInfo,
    val createdAt: String,
) {

    companion object {
        fun from(like: Like, product: Product, memberIdValue: String): LikeInfo {
            return LikeInfo(
                id = like.id,
                memberId = memberIdValue,
                product = ProductInfo.from(product),
                createdAt = like.createdAt.toString()
            )
        }
    }
}
