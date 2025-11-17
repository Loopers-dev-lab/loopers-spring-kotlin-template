package com.loopers.domain.point

import com.loopers.domain.common.vo.Money
import java.math.BigDecimal

object PointFixture {
    private const val DEFAULT_USER_ID = 1L
    private const val DEFAULT_BALANCE = 100L

    fun create(
        userId: Long = DEFAULT_USER_ID,
        balance: BigDecimal = BigDecimal.valueOf(DEFAULT_BALANCE),
    ): PointModel = PointModel(
        refUserId = userId,
        balance = Money(balance),
    )
}
