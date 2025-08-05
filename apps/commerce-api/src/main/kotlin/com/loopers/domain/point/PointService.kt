package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class PointService(
    private val pointRepository: PointRepository,
) {

    fun getMe(userId: Long): Point? {
        return pointRepository.findByUserId(userId)
    }

    fun get(userId: Long): Point {
        return pointRepository.findByUserId(userId)
            ?: let {
                throw CoreException(errorType = ErrorType.CONFLICT, customMessage = "[userId] 포인트가 존재하지 않습니다 입니다.")
            }
    }

    @Transactional
    fun charge(userId: Long, amount: Int): Point {
        return pointRepository.findByUserId(userId)
            ?.apply { charge(amount) }
            ?: pointRepository.save(Point.create(userId, amount))
    }
}
