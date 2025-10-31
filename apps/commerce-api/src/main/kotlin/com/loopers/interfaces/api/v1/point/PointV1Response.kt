package com.loopers.interfaces.api.v1.point

import com.loopers.domain.point.PointResult

object PointV1Response {

    data class Charge(
        val userId: String,
        val amount: Long,
    ) {
        companion object {
            fun from(pointResult: PointResult) = with(pointResult) {
                Charge(
                    userId = userId,
                    amount = amount,
                )
            }
        }
    }

    data class Get(
        val amount: Long,
    ) {
        companion object {
            fun from(pointResult: PointResult) = with(pointResult) {
                Get(
                    amount = amount,
                )
            }
        }
    }
}
