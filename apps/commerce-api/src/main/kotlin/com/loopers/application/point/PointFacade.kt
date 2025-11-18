package com.loopers.application.point

import com.loopers.domain.point.PointCommand
import com.loopers.domain.point.PointResult
import com.loopers.domain.point.PointService
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointFacade(
    private val pointService: PointService,
    private val userService: UserService,
) {

    @Transactional
    fun charge(command: PointCommand.Charge): PointResult {
        val user = userService.getMyInfo(command.userId)
        return pointService.charge(command.amount, user.id).let { PointResult.from(it, user.userId) }
    }

    @Transactional
    fun getBy(userId: String): PointResult {
        val user = userService.getMyInfo(userId)

        return pointService.getBy(user.id)
            ?.let { PointResult.from(it, user.userId) }
            ?: PointResult.init(userId)
    }
}
