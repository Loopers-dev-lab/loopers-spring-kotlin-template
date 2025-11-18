package com.loopers.application.like

import com.loopers.application.product.ProductInfo
import com.loopers.domain.like.Like

data class LikeInfo(
    val id: Long,
    val memberId: Long,
    val product: ProductInfo,
    val createdAt: String,
) {

    companion object {
        fun from(like: Like): LikeInfo {
            return LikeInfo(
                id = like.id,
                memberId = like.member.id,
                product = ProductInfo.from(like.product),
                createdAt = like.createdAt.toString()
            )
        }
    }
}
