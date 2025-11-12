package com.loopers.support.fixtures

import com.loopers.domain.point.Point

object PointFixtures {
    fun createPoint(
        id: Long = 1L,
        amount: Long = 1000L,
        userId: Long = 1L,
    ): Point {
        return Point.create(
            amount = 1000L,
            userId = 1L,
        ).withId(id)
    }
}
