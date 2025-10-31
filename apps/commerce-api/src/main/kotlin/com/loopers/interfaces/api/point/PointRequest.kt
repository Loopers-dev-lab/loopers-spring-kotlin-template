package com.loopers.interfaces.api.point

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

class PointRequest {
    data class PointChargeRequestDto(
        @field:Schema(description = "충전할 금액", example = "1000")
        @field:NotNull(message = "충전 금액은 필수값입니다.")
        @field:Min(value = 1, message = "충전 금액은 1 이상이어야 합니다.")
        val amount: Long,
    )
}
