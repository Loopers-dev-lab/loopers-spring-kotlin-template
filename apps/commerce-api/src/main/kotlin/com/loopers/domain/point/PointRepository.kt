package com.loopers.domain.point

interface PointRepository {

    fun save(point: PointModel): PointModel

    fun findByUserId(userId: Long): PointModel?
}
