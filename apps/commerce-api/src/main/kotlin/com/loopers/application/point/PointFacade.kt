package com.loopers.application.point

import com.loopers.domain.point.Money
import com.loopers.domain.point.PointService
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PointFacade(
    private val userRepository: UserRepository,
    private val pointService: PointService,
) {
    fun charge(userId: Long, amount: Money): PointV1Info.Charge {
        return pointService.charge(userId, amount)
            .let { PointV1Info.Charge.from(it) }
    }

    fun getBalance(userId: Long): PointV1Info.GetBalance {
        return pointService.getPointAccountBy(userId)
            ?.let { PointV1Info.GetBalance.from(it) }
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $userId] 유저의 포인트를 찾을 수 없습니다.")
    }
}
