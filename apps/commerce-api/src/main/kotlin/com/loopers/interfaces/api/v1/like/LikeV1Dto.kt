package com.loopers.interfaces.api.v1.like

import com.loopers.application.product.ProductResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

class LikeV1Dto {
    @Schema(description = "좋아요한 상품 목록 응답")
    data class LikedProductListResponse(
        @Schema(description = "좋아요한 상품 목록")
        val items: List<LikedProductInfo>,
    ) {
        companion object {
            fun from(
                content: List<ProductResult.LikedInfo>,
            ): LikedProductListResponse = LikedProductListResponse(
                items = content.map { LikedProductInfo.from(it) },
            )
        }
    }

    @Schema(description = "좋아요한 상품 정보")
    data class LikedProductInfo(
        @Schema(description = "상품 ID")
        val id: Long,
        @Schema(description = "상품명")
        val name: String,
        @Schema(description = "상품 가격")
        val price: Long,
        @Schema(description = "브랜드명")
        val brandName: String,
        @Schema(description = "좋아요 등록 시간")
        val likedCreatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: ProductResult.LikedInfo): LikedProductInfo = LikedProductInfo(
                id = info.id,
                name = info.name,
                price = info.price,
                brandName = info.brandName,
                likedCreatedAt = info.likedCreatedAt,
            )
        }
    }
}
