package com.loopers.application.point

import com.loopers.domain.point.PointService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointFacade(
    private val pointService: PointService,
    private val userService: UserService,
) {

    fun getMe(userId: Long): PointInfo? {
        val point = pointService.getMe(userId)
            ?: return PointInfo.of(userId, 0)
        return PointInfo.from(point)
    }

    @Transactional
    fun charge(charge: PointInfo.Charge): PointInfo? {
        val user = userService.getMe(charge.userName)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[userName = ${charge.userName}] 존재하지 않는 유저입니다.")

        val point = pointService.charge(user.id, charge.amount)
        return PointInfo.from(point)
    }
}
