package com.loopers.interfaces.api.point

import com.loopers.application.point.PointInfo
import java.math.BigDecimal

class PointInfoDto {
    data class Response(val balance: BigDecimal) {
        companion object {
            fun from(info: PointInfo): Response = Response(
                balance = info.balance,
            )
        }
    }
}
