package com.loopers.domain.point

import java.math.BigDecimal

class PointResult {

    data class PointInfoResult(
        val userId: Long,
        val balance: BigDecimal,
    ) {
        companion object {
            fun from(point: Point): PointInfoResult {
                return PointInfoResult(
                    userId = point.userId,
                    balance = point.balance,
                )
            }
        }
    }
}
