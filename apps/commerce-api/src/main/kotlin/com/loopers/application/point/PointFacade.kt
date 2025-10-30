package com.loopers.application.point

import com.loopers.domain.point.PointService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PointFacade(private val pointService: PointService, private val userService: UserService) {
    fun getPointByUserId(loginId: String): PointInfo {
        val user = userService.getUser(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다.")

        val point = pointService.getPointByUserId(user.id)

        return PointInfo.from(point?.balance ?: 0L)
    }

    fun charge(loginId: String, amount: Long): PointInfo {
        val user = userService.getUser(loginId)
        require(user != null) {
            throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다.")
        }
        val point = pointService.charge(user.id, amount)

        return PointInfo.from(point.balance)
    }
}
