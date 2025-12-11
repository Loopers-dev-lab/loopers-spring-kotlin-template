package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
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
        val pointAccount = pointAccountRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[id = $userId] 유저를 찾을 수 없습니다.",
            )

        pointAccount.charge(amount)

        return pointAccountRepository.save(pointAccount)
    }

    @Transactional
    fun deduct(userId: Long, amount: Money): PointAccount {
        val pointAccount = pointAccountRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[id = $userId] 유저를 찾을 수 없습니다.",
            )
        pointAccount.deduct(amount)

        return pointAccountRepository.save(pointAccount)
    }

    fun getPointAccountBy(userId: Long): PointAccount? {
        return pointAccountRepository.findByUserId(userId)
    }

    /**
     * 포인트를 복구합니다. 결제 실패 시 차감했던 포인트를 되돌립니다.
     *
     * @param userId 사용자 ID
     * @param amount 복구할 금액
     * @return 복구된 포인트 계좌
     */
    @Transactional
    fun restore(userId: Long, amount: Money): PointAccount {
        val pointAccount = pointAccountRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[id = $userId] 유저를 찾을 수 없습니다.",
            )

        pointAccount.restore(amount)

        return pointAccountRepository.save(pointAccount)
    }
}
