package com.loopers.domain.point

import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PointService(
    private val pointRepository: PointRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun charge(command: PointCommand.Charge): Point {
        if (!userRepository.exist(command.userId)) {
            throw CoreException(ErrorType.NOT_FOUND)
        }

        val point = pointRepository.getBy(command.userId)
        if (point == null) {
            return pointRepository.save(Point.create(command.amount, command.userId))
        } else {
            point.charge(command.amount)
            return point
        }
    }

    @Transactional(readOnly = true)
    fun getBy(userId: String): Point? {
        return pointRepository.getBy(userId)
    }
}
