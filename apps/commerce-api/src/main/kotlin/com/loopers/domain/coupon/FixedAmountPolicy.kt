package com.loopers.domain.coupon

import com.loopers.support.values.Money
import org.springframework.stereotype.Component

/**
 * 정액 할인 정책
 *
 * FIXED_AMOUNT 타입의 쿠폰에 대해 고정 금액 할인을 적용합니다.
 * 할인 금액이 주문 금액을 초과하는 경우 주문 금액만큼만 할인합니다.
 */
@Component
class FixedAmountPolicy : DiscountPolicy {

    override fun supports(coupon: Coupon): Boolean {
        return coupon.discountAmount.type == DiscountType.FIXED_AMOUNT
    }

    override fun calculate(orderAmount: Money, coupon: Coupon): Money {
        val discountValue = Money.krw(coupon.discountAmount.value)
        return discountValue.min(orderAmount)
    }
}
