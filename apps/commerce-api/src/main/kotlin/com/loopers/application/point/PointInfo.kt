package com.loopers.application.point

import com.loopers.domain.point.Point

data class PointInfo(
    val userId: Long,
    val amount: Int,
) {
    companion object {
        fun from(point: Point?): PointInfo? {
            return point
                ?.let { PointInfo(point.userId, point.amount.value) }
        }

        fun of(userId: Long, amount: Int): PointInfo {
            return PointInfo(userId, amount)
        }
    }

    data class Charge(
        val userName: String,
        val amount: Int,
    ) {

        companion object {
            fun of(userName: String, amount: Int): Charge {
                return Charge(userName, amount)
            }
        }
    }
}
