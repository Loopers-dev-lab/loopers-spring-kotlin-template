package com.loopers.interfaces.api.v1.ranking

import com.loopers.application.ranking.dto.RankingResult
import io.swagger.v3.oas.annotations.media.Schema

class RankingV1Dto {
    @Schema(description = "랭킹 목록 응답")
    data class RankingListResponse(
        @Schema(description = "랭킹 목록")
        val items: List<RankingItem>,
    ) {
        companion object {
            fun from(content: List<RankingResult.RankedInfo>): RankingListResponse = RankingListResponse(
                items = content.map { RankingItem.from(it) },
            )
        }
    }

    @Schema(description = "랭킹 정보")
    data class RankingItem(
        @Schema(description = "순위")
        val rank: Long,
        @Schema(description = "랭킹 점수")
        val score: Double,
        @Schema(description = "상품 ID")
        val productId: Long,
        @Schema(description = "상품명")
        val name: String,
        @Schema(description = "상품 가격")
        val price: Long,
        @Schema(description = "브랜드명")
        val brandName: String,
    ) {
        companion object {
            fun from(info: RankingResult.RankedInfo): RankingItem = RankingItem(
                rank = info.rank,
                score = info.score,
                productId = info.productId,
                name = info.name,
                price = info.price,
                brandName = info.brandName,
            )
        }
    }
}
