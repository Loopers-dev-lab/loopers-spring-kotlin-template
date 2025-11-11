package com.loopers.interfaces.api.point

import com.loopers.domain.point.PointResult
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

class PointResponse {
    data class PointResponseDto(
        @get:Schema(description = "보유 포인트", example = "5000.00")
        val balance: BigDecimal,
    ) {
        companion object {
            fun from(result: PointResult.PointInfoResult): PointResponseDto {
                return PointResponseDto(
                    balance = result.balance,
                )
            }
        }
    }
}
