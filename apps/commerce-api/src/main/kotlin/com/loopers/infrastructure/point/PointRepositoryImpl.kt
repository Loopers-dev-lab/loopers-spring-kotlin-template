package com.loopers.infrastructure.point

import com.loopers.domain.point.PointModel
import com.loopers.domain.point.PointRepository
import org.springframework.stereotype.Component

@Component
class PointRepositoryImpl(private val pointJpaRepository: PointJpaRepository) : PointRepository {
    override fun save(point: PointModel): PointModel = pointJpaRepository.save(point)

    override fun findByUserId(userId: Long): PointModel? = pointJpaRepository.findByUserId(userId)
}
