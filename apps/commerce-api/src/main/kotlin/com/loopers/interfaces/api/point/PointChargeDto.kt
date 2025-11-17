package com.loopers.interfaces.api.point

import com.loopers.application.point.PointInfo
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

class PointChargeDto {
    data class Request(
        @field:NotNull(message = "포인트 충전량은 필수입니다.")
        @field:Positive(message = "포인트 충전량은 0보다 커야합니.")
        var balance: BigDecimal,
    )

    data class Response(val balance: BigDecimal) {
        companion object {
            fun from(info: PointInfo): Response = Response(
                balance = info.balance,
            )
        }
    }
}
