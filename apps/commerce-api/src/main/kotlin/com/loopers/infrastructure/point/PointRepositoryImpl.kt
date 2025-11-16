package com.loopers.infrastructure.point

import com.loopers.domain.point.Point
import com.loopers.domain.point.PointRepository
import org.springframework.stereotype.Repository

@Repository
class PointRepositoryImpl(
    private val pointJpaRepository: PointJpaRepository,
) : PointRepository {

    override fun save(point: Point): Point {
        return pointJpaRepository.save(point)
    }

    override fun getBy(userId: Long): Point? {
        return pointJpaRepository.findByUserId(userId)
    }
}
