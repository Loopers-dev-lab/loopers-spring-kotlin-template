package com.loopers.domain.point

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

    @Transactional
    fun charge(userId: Long, amount: Int): Point {
        return pointRepository.findByUserId(userId)
            ?.apply { charge(amount) }
            ?: pointRepository.save(Point.create(userId, amount))
    }
}
