package com.loopers.domain.point

object PointFixture {
    private const val DEFAULT_USER_ID = 1L
    private const val DEFAULT_BALANCE = 100L

    fun create(
        userId: Long = DEFAULT_USER_ID,
        balance: Long = DEFAULT_BALANCE,
    ): PointModel = PointModel(
            userId = userId,
            balance = balance,
        )
}
