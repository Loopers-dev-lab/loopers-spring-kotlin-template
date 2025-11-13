package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PointService(
    private val pointRepository: PointRepository,
) {

    @Transactional
    fun charge(amount: Long, userId: Long): Point {
        val point = pointRepository.getBy(userId) ?: return pointRepository.save(Point.create(amount, userId))
        point.charge(amount)
        return point
    }

    @Transactional
    fun init(userId: Long) {
        pointRepository.save(Point.init(userId))
    }

    @Transactional
    fun use(amount: Long, userId: Long) {
        val point = pointRepository.getBy(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND)
        point.use(amount)
    }

    @Transactional(readOnly = true)
    fun getBy(userId: Long): Point? {
        return pointRepository.getBy(userId)
    }
}
