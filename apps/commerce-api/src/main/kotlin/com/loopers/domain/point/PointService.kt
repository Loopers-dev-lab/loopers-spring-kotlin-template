package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointService(
    private val pointAccountRepository: PointAccountRepository,
) {
    @Transactional
    fun createPointAccount(userId: Long): PointAccount {
        return PointAccount.create(userId)
            .let { pointAccountRepository.save(it) }
    }

    @Transactional
    fun charge(userId: Long, amount: Money): PointAccount {
        val pointAccount = pointAccountRepository.findByUserId(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[id = $userId] 유저를 찾을 수 없습니다.",
            )

        pointAccount.charge(amount)

        return pointAccountRepository.save(pointAccount)
    }

    fun getPointAccountBy(userId: Long): PointAccount? {
        return pointAccountRepository.findByUserId(userId)
    }
}
