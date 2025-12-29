package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingInfo
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Ranking API DTO
 */
object RankingV1Dto {

    /**
     * 랭킹 응답 DTO
     */
    @Schema(description = "랭킹 정보")
    data class RankingResponse(
        @Schema(description = "상품 ID", example = "1")
        val productId: Long,

        @Schema(description = "상품명", example = "나이키 에어맥스")
        val productName: String,

        @Schema(description = "가격", example = "15000")
        val price: Long,

        @Schema(description = "재고", example = "50")
        val stock: Int,

        @Schema(description = "좋아요 수", example = "120")
        val likesCount: Int,

        @Schema(description = "순위 (1부터 시작)", example = "1")
        val rank: Long,

        @Schema(description = "랭킹 점수", example = "125.5")
        val score: Double,
    ) {
        companion object {
            fun from(info: RankingInfo): RankingResponse {
                val product = info.product
                return RankingResponse(
                    productId = product.id,
                    productName = product.name,
                    price = product.price.amount,
                    stock = product.stock,
                    likesCount = product.likesCount,
                    rank = info.rank,
                    score = info.score,
                )
            }
        }
    }
}
