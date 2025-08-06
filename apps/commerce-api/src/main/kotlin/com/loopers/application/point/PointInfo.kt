package com.loopers.application.point

import com.loopers.domain.point.Point
import java.math.BigDecimal

data class PointInfo(
    val userId: Long,
    val amount: BigDecimal,
) {
    companion object {
        fun from(point: Point?): PointInfo? {
            return point
                ?.let { PointInfo(point.userId, point.amount.value) }
        }

        fun of(userId: Long, amount: BigDecimal): PointInfo {
            return PointInfo(userId, amount)
        }
    }

    data class Charge(
        val userName: String,
        val amount: BigDecimal,
    ) {

        companion object {
            fun of(userName: String, amount: BigDecimal): Charge {
                return Charge(userName, amount)
            }
        }
    }
}
