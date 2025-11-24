package com.loopers.application.like

import com.loopers.application.product.ProductInfo
import com.loopers.domain.like.Like

data class LikeInfo(
    val id: Long,
    val memberId: String,
    val product: ProductInfo,
    val createdAt: String,
) {

    companion object {
        fun from(like: Like): LikeInfo {
            return LikeInfo(
                id = like.id,
                memberId = like.member.memberId.value,
                product = ProductInfo.from(like.product),
                createdAt = like.createdAt.toString()
            )
        }
    }
}
