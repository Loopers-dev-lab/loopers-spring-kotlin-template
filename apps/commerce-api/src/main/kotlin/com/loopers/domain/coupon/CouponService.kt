package com.loopers.domain.coupon

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class CouponService(private val couponRepository: CouponRepository) {

    @Transactional
    fun calculateDiscountPrice(couponId: Long, totalPrice: BigDecimal): BigDecimal {
        val coupon = couponRepository.getNotUsedByCouponIdWithPessimisticLock(couponId)

        coupon.updateUsed()
        couponRepository.save(coupon)

        return coupon.calculateDiscountPrice(totalPrice)
    }
}
