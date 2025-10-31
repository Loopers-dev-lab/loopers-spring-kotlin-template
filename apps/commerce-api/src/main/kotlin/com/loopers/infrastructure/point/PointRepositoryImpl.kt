package com.loopers.infrastructure.point

import com.loopers.domain.point.Point
import com.loopers.domain.point.PointRepository
import com.loopers.domain.user.UserId
import org.springframework.stereotype.Repository

@Repository
class PointRepositoryImpl(
    private val pointJpaRepository: PointJpaRepository,
) : PointRepository {

    override fun save(point: Point): Point {
        return pointJpaRepository.save(point)
    }

    override fun getBy(userId: String): Point? {
        return pointJpaRepository.findByUserId(UserId(userId))
    }
}
