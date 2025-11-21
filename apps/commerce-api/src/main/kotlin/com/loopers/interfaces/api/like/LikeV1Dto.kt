package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeInfo

class LikeV1Dto {
    data class LikeResponse(
        val id: Long,
        val memberId: String,
        val productId: Long,
        val productName: String,
        val price: Long,
        val createdAt: String,
    ) {
        companion object {
            fun from(info: LikeInfo): LikeResponse {
                return LikeResponse(
                    id = info.id,
                    memberId = info.memberId,
                    productId = info.product.id,
                    productName = info.product.name,
                    price = info.product.price.amount,
                    createdAt = info.createdAt
                )
            }
        }
    }
}
