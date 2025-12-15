package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class CouponService(private val couponRepository: CouponRepository) {

    @Transactional
    fun calculateDiscountPrice(couponId: Long, userId: Long, totalPrice: BigDecimal): BigDecimal {
        val coupon = couponRepository.findByCouponIdAndUserId(couponId, userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰이 존재하지 않습니다.")

        return coupon.calculateDiscountPrice(totalPrice)
    }

    @Transactional
    fun useCoupon(couponId: Long, userId: Long): CouponModel {
        val coupon = couponRepository.getNotUsedByCouponIdWithPessimisticLock(couponId, userId)
        coupon.updateUsed()
        return couponRepository.save(coupon)
    }
}
