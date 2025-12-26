package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingCriteria
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

class RankingV1Request {

    data class UpdateWeight(
        @field:Schema(
            description = "조회 가중치 (0.0 ~ 1.0)",
            example = "0.10",
        )
        @field:DecimalMin(value = "0.0", message = "조회 가중치는 0.0 이상이어야 합니다")
        @field:DecimalMax(value = "1.0", message = "조회 가중치는 1.0 이하여야 합니다")
        val viewWeight: BigDecimal,

        @field:Schema(
            description = "좋아요 가중치 (0.0 ~ 1.0)",
            example = "0.20",
        )
        @field:DecimalMin(value = "0.0", message = "좋아요 가중치는 0.0 이상이어야 합니다")
        @field:DecimalMax(value = "1.0", message = "좋아요 가중치는 1.0 이하여야 합니다")
        val likeWeight: BigDecimal,

        @field:Schema(
            description = "주문 가중치 (0.0 ~ 1.0)",
            example = "0.60",
        )
        @field:DecimalMin(value = "0.0", message = "주문 가중치는 0.0 이상이어야 합니다")
        @field:DecimalMax(value = "1.0", message = "주문 가중치는 1.0 이하여야 합니다")
        val orderWeight: BigDecimal,
    ) {
        fun toCriteria(): RankingCriteria.UpdateWeight {
            return RankingCriteria.UpdateWeight(
                viewWeight = viewWeight,
                likeWeight = likeWeight,
                orderWeight = orderWeight,
            )
        }
    }
}
