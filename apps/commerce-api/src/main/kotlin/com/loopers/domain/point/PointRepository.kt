package com.loopers.domain.point

interface PointRepository {
    fun save(point: Point): Point
    fun findByUserId(userId: Long): Point?
}
