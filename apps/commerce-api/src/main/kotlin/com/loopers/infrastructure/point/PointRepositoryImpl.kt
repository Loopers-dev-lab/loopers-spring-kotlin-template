package com.loopers.infrastructure.point

import com.loopers.domain.point.PointModel
import com.loopers.domain.point.PointRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PointRepositoryImpl(private val pointJpaRepository: PointJpaRepository) : PointRepository {
    override fun save(point: PointModel): PointModel = pointJpaRepository.save(point)

    override fun findByUserId(userId: Long): PointModel? = pointJpaRepository.findByRefUserId(userId)

    override fun getUserBy(userId: Long): PointModel =
        pointJpaRepository.findByRefUserId(userId) ?: throw CoreException(ErrorType.NOT_FOUND, "유저가 존재하지 않습니다.")

    override fun getUserByUserIdWithPessimisticLock(userId: Long): PointModel =
        pointJpaRepository.getUserByUserIdWithPessimisticLock(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 유저에 대한 포인트가 존재하지 않습니다.")
}
