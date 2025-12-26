package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingInfo
import java.math.BigDecimal

class RankingV1Response {

    data class GetRankings(
        val rankings: List<RankingDto>,
        val hasNext: Boolean,
    ) {
        companion object {
            fun from(info: RankingInfo.FindRankings): GetRankings {
                return GetRankings(
                    rankings = info.rankings.map { RankingDto.from(it) },
                    hasNext = info.hasNext,
                )
            }
        }
    }

    data class RankingDto(
        val rank: Int,
        val productId: Long,
        val name: String,
        val price: Int,
        val status: String,
        val brandId: Long,
        val brandName: String,
        val score: BigDecimal,
    ) {
        companion object {
            fun from(unit: RankingInfo.RankingUnit): RankingDto {
                return RankingDto(
                    rank = unit.rank,
                    productId = unit.productId,
                    name = unit.name,
                    price = unit.price.amount.toInt(),
                    status = unit.status.name,
                    brandId = unit.brandId,
                    brandName = unit.brandName,
                    score = unit.score,
                )
            }
        }
    }

    data class GetWeight(
        val viewWeight: BigDecimal,
        val likeWeight: BigDecimal,
        val orderWeight: BigDecimal,
    ) {
        companion object {
            fun from(info: RankingInfo.FindWeight): GetWeight {
                return GetWeight(
                    viewWeight = info.viewWeight,
                    likeWeight = info.likeWeight,
                    orderWeight = info.orderWeight,
                )
            }
        }
    }

    data class UpdateWeight(
        val viewWeight: BigDecimal,
        val likeWeight: BigDecimal,
        val orderWeight: BigDecimal,
    ) {
        companion object {
            fun from(info: RankingInfo.UpdateWeight): UpdateWeight {
                return UpdateWeight(
                    viewWeight = info.viewWeight,
                    likeWeight = info.likeWeight,
                    orderWeight = info.orderWeight,
                )
            }
        }
    }
}
