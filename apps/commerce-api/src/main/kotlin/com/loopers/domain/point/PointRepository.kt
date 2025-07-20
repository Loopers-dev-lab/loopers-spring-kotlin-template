package com.loopers.domain.point

interface PointRepository {
    fun findByUserId(userId: Long): Point?

    fun save(point: Point): Point
}
