package com.loopers.interfaces.api.point

import com.loopers.domain.point.PointResult
import io.swagger.v3.oas.annotations.media.Schema

class PointResponse {
    data class PointResponseDto(
        @get:Schema(description = "보유 포인트", example = "5000")
        val amount: Long,
    ) {
        companion object {
            fun from(result: PointResult.PointInfoResult): PointResponseDto {
                return PointResponseDto(
                    amount = result.amount,
                )
            }
        }
    }
}
