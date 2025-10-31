package com.loopers.domain.point

data class PointResult(
    val userId: String,
    val amount: Long,
) {
    companion object {
        fun init(userId: String): PointResult {
            return PointResult(
                userId = userId,
                amount = 0L,
            )
        }

        fun from(point: Point) = with(point) {
            PointResult(
                userId = userId.value,
                amount = amount.value,
            )
        }
    }
}
