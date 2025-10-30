package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PointService(private val pointRepository: PointRepository) {
    fun getPointByUserId(userId: Long): PointModel? = pointRepository.findByUserId(userId)

    fun createPoint(userId: Long, balance: Long): PointModel {
        val point = PointModel(userId, balance)
        return pointRepository.save(point)
    }

    fun charge(userId: Long, point: Long): PointModel = pointRepository.findByUserId(userId)
        ?.also { it.charge(point) }
        ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 유저입니다.")
}
