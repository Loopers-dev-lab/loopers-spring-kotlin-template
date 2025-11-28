package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class CouponService(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {

    @Transactional
    fun useCouponAndCalculateFinalAmount(userCouponId: Long, originalAmount: BigDecimal): BigDecimal {
        val userCoupon = userCouponRepository.findByIdWithLock(userCouponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자 쿠폰입니다. [userCouponId: $userCouponId]")

        if (!userCoupon.isAvailable()) {
            throw CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다. [userCouponId: $userCouponId]")
        }

        userCoupon.use()

        val coupon = couponRepository.findById(userCoupon.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다. [couponId: ${userCoupon.couponId}]")

        val discountAmount = coupon.calculateDiscount(originalAmount)
        return originalAmount.subtract(discountAmount)
    }
}
