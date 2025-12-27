package com.loopers.infrastructure.point

import com.loopers.domain.point.Point
import com.loopers.domain.point.PointRepository
import org.springframework.stereotype.Repository

@Repository
class PointRepositoryImpl(private val pointJpaRepository: PointJpaRepository) : PointRepository {
    override fun findByUserId(
        userId: Long,
    ): Point? = pointJpaRepository.findByUserId(userId)

    override fun findByUserIdWithLock(
        userId: Long,
    ): Point? = pointJpaRepository.findByUserIdWithLock(userId)

    override fun save(point: Point): Point = pointJpaRepository.save(point)
}
