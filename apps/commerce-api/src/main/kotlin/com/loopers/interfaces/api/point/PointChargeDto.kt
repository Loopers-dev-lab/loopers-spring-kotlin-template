package com.loopers.interfaces.api.point

import com.loopers.application.point.PointInfo
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

class PointChargeDto {
    data class Request(
        @field:NotBlank(message = "포인트 충전량은 필수 입니다.")
        val balance: BigDecimal,
    )

    data class Response(val balance: BigDecimal) {
        companion object {
            fun from(info: PointInfo): Response = Response(
                balance = info.balance,
            )
        }
    }
}
