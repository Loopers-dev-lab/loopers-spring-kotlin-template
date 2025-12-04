package com.loopers.domain.coupon

import com.loopers.support.values.Money
import org.springframework.stereotype.Component

/**
 * 정률 할인 정책
 *
 * RATE 타입의 쿠폰에 대해 백분율 할인을 적용합니다.
 * 계산 결과는 원 단위로 반올림되며, 주문 금액을 초과할 수 없습니다.
 */
@Component
class RatePolicy : DiscountPolicy {

    override fun supports(coupon: Coupon): Boolean {
        return coupon.discountAmount.type == DiscountType.RATE
    }

    override fun calculate(orderAmount: Money, coupon: Coupon): Money {
        return orderAmount.applyPercentage(coupon.discountAmount.value)
            .min(orderAmount)
            .round(0)
    }
}
