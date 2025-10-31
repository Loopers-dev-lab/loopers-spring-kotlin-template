package com.loopers.domain.point

import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointService(
    private val userRepository: UserRepository,
    private val pointWalletRepository: PointWalletRepository,
) {
    @Transactional
    fun charge(userId: Long, amount: Money): PointWallet {
        val user = userRepository.findById(userId)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $userId] 유저를 찾을 수 없습니다.")

        val pointWallet = pointWalletRepository.getByUserId(userId)

        val transaction = PointTransaction.charge(userId, amount)
        pointWallet.addTransaction(transaction)
        return pointWalletRepository.save(pointWallet)
    }
}
