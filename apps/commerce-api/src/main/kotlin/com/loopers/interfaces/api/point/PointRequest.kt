package com.loopers.interfaces.api.point

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

class PointRequest {
    data class PointChargeRequestDto(
        @field:Schema(description = "충전할 금액", example = "1000.00")
        @field:NotNull(message = "충전 금액은 필수값입니다.")
        @field:DecimalMin(value = "0.01", message = "충전 금액은 0보다 커야 합니다.")
        val amount: BigDecimal,
    )
}
