package com.loopers.domain.point

import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class PointService(
    private val pointRepository: PointRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun findByUserId(userId: Long): Point? {
        return pointRepository.findByUserId(userId)
    }

    @Transactional
    fun chargePoint(userId: Long, amount: BigDecimal): Point {
        val user = userRepository.findById(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다. [userId: $userId]")

        val point = pointRepository.findByUserId(user.id)
            ?: Point.of(userId = user.id, initialBalance = BigDecimal.ZERO)

        point.charge(amount)
        return pointRepository.save(point)
    }
}
