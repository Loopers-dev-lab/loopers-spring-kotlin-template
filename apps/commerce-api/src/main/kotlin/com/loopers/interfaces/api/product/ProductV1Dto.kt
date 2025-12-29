package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo
import io.swagger.v3.oas.annotations.media.Schema

class ProductV1Dto {
    data class ProductResponse(
        @Schema(description = "상품 ID", example = "1")
        val id: Long,
        @Schema(description = "상품명", example = "나이키 에어맥스")
        val name: String,
        @Schema(description = "상품 설명")
        val description: String?,
        @Schema(description = "가격", example = "15000")
        val price: Long,
        @Schema(description = "재고", example = "50")
        val stock: Int,
        @Schema(description = "좋아요 수", example = "120")
        val likesCount: Int,
        @Schema(description = "현재 순위 (1부터 시작, 순위권 밖이면 null)", example = "3")
        val rank: Long? = null, // 기존 API 호환성을 위해 기본값 null
        @Schema(description = "랭킹 점수 (순위권 밖이면 null)", example = "98.5")
        val score: Double? = null
    ) {
        companion object {
            /**
             * ProductInfo로부터 변환 (랭킹 정보 없음)
             */
            fun from(info: ProductInfo): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    name = info.name,
                    description = info.description,
                    price = info.price.amount,
                    stock = info.stock,
                    likesCount = info.likesCount,
                    rank = null,
                    score = null
                )
            }

            /**
             * ProductInfo와 랭킹 정보로부터 변환
             */
            fun fromWithRanking(info: ProductInfo, rank: Long?, score: Double?): ProductResponse {
                return ProductResponse(
                    id = info.id,
                    name = info.name,
                    description = info.description,
                    price = info.price.amount,
                    stock = info.stock,
                    likesCount = info.likesCount,
                    rank = rank,
                    score = score
                )
            }
        }
    }
}
