package com.loopers.domain.point

class PointResult {

    data class PointInfoResult(
        val userId: Long,
        val amount: Long,
    ) {
        companion object {
            fun from(point: Point): PointInfoResult {
                return PointInfoResult(
                    userId = point.userId,
                    amount = point.amount,
                )
            }
        }
    }
}
