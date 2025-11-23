package com.loopers.support.fixtures

import com.loopers.domain.point.Point

object PointFixtures {
    fun createPoint(
        userId: Long = 1L,
        amount: Long = 1000L,
    ): Point {
        return Point.create(
            userId = userId,
            amount = amount,
        )
    }

    fun createPoint(
        id: Long = 1L,
        amount: Long = 1000L,
        userId: Long = 1L,
    ): Point {
        return Point.create(
            amount = amount,
            userId = userId,
        ).withId(id)
    }
}
