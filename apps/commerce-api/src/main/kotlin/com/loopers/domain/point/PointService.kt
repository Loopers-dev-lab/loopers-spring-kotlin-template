package com.loopers.domain.point

import com.loopers.domain.order.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PointService(private val pointRepository: PointRepository) {
    @Transactional
    fun validateUserPoint(userId: Long, totalAmount: Money) {
        val point = pointRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다: $userId")

        if (!point.canDeduct(totalAmount)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "포인트 부족: 현재 잔액 ${point.balance.amount}, 필요 금액 ${totalAmount.amount}",
            )
        }
    }

    @Transactional
    fun deductPoint(userId: Long, totalAmount: Money): Point {
        val lockedPoint = pointRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다: $userId")
        lockedPoint.deduct(totalAmount)
        return pointRepository.save(lockedPoint)
    }

    @Transactional
    fun chargePoint(userId: Long, amount: Money): Point {
        val point = pointRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다: $userId")
        point.charge(amount)
        return pointRepository.save(point)
    }

    fun getPoint(userId: Long): Point = pointRepository.findByUserId(userId)
        ?: throw CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다: $userId")
}
