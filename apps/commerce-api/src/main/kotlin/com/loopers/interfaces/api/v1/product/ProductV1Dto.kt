package com.loopers.interfaces.api.v1.product

import com.loopers.application.product.ProductResult
import io.swagger.v3.oas.annotations.media.Schema

class ProductV1Dto {
    @Schema(description = "상품 목록 응답")
    data class ProductListResponse(
        @Schema(description = "상품 목록")
        val items: List<ProductInfo>,
    ) {
        companion object {
            fun from(
                content: List<ProductResult.ListInfo>,
            ): ProductListResponse = ProductListResponse(
                items = content.map { ProductInfo.from(it) },
            )
        }
    }

    @Schema(description = "상품 정보")
    data class ProductInfo(
        @Schema(description = "상품 ID")
        val id: Long,
        @Schema(description = "상품명")
        val name: String,
        @Schema(description = "상품 가격")
        val price: Long,
        @Schema(description = "브랜드명")
        val brandName: String,
        @Schema(description = "좋아요 수")
        val likeCount: Long,
    ) {
        companion object {
            fun from(info: ProductResult.ListInfo): ProductInfo = ProductInfo(
                id = info.id,
                name = info.name,
                price = info.price,
                brandName = info.brandName,
                likeCount = info.likeCount,
            )
        }
    }

    @Schema(description = "상품 상세 응답")
    data class ProductDetailResponse(
        @Schema(description = "상품 ID")
        val id: Long,
        @Schema(description = "상품명")
        val name: String,
        @Schema(description = "상품 가격")
        val price: Long,
        @Schema(description = "브랜드명")
        val brandName: String,
        @Schema(description = "좋아요 수")
        val likeCount: Long,
        @Schema(description = "내가 좋아요 했는지 여부")
        val likedByMe: Boolean,
        @Schema(description = "랭킹 순위 (랭킹에 없으면 null)")
        val rank: Long?,
        @Schema(description = "랭킹 점수 (랭킹에 없으면 null)")
        val score: Double?,
    ) {
        companion object {
            fun from(info: ProductResult.DetailInfo): ProductDetailResponse = ProductDetailResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                brandName = info.brandName,
                likeCount = info.likeCount,
                likedByMe = info.likedByMe,
                rank = info.rank,
                score = info.score,
            )
        }
    }
}
