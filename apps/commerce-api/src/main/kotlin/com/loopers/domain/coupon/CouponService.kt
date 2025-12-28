package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.PessimisticLockException
import org.springframework.dao.CannotAcquireLockException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val userCouponRepository: UserCouponRepository,
) {

    /**
     * 사용자 쿠폰을 조회하고 사용 처리한다 (비관적 락 사용)
     * 동시성 제어를 위해 비관적 락을 사용하여 중복 사용을 방지한다
     * 락 획득 실패 시 최대 3회까지 재시도 (100ms, 200ms, 400ms 백오프)
     */
    @Transactional(timeout = 5)
    @Retryable(
        retryFor = [PessimisticLockException::class, CannotAcquireLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun useUserCoupon(userId: Long, userCouponId: Long): UserCoupon {
        val userCoupon = userCouponRepository.findByIdAndUserIdWithLock(userCouponId, userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "사용자 쿠폰을 찾을 수 없습니다: userCouponId=$userCouponId, userId=$userId",
            )

        // 사용 가능 여부 확인
        if (!userCoupon.canUse()) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "사용할 수 없는 쿠폰입니다",
            )
        }

        // 쿠폰 사용 처리
        userCoupon.use()

        return userCouponRepository.save(userCoupon)
    }

    /**
     * 사용자 쿠폰 조회 (사용 여부와 관계없이)
     */
    fun getUserCoupon(userId: Long, userCouponId: Long): UserCoupon {
        val userCoupon = userCouponRepository.findById(userCouponId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "사용자 쿠폰을 찾을 수 없습니다: $userCouponId",
            )

        if (userCoupon.userId != userId) {
            throw CoreException(
                errorType = ErrorType.FORBIDDEN,
                customMessage = "다른 사용자의 쿠폰입니다",
            )
        }

        return userCoupon
    }
}
