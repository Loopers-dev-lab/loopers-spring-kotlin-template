package com.loopers.interfaces.api.point

import com.loopers.application.point.PointInfo
import jakarta.validation.constraints.Min
import java.math.BigDecimal

class PointV1Dto {
    data class PointResponse(
        val userId: Long,
        val amount: BigDecimal,
    ) {
        companion object {
            fun from(point: PointInfo?): PointResponse? {
                return point
                    ?.let { PointResponse(it.userId, it.amount) }
            }
        }
    }

    data class ChargeRequest(
        @field:Min(value = 0, message = "0 이하")
        val amount: BigDecimal,
    ) {
        fun toCharge(userName: String): PointInfo.Charge {
            return PointInfo.Charge.of(userName, amount)
        }
    }
}
