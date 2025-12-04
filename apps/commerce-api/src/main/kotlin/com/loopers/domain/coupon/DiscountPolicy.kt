package com.loopers.domain.coupon

import com.loopers.support.values.Money

/**
 * 할인 계산 전략 인터페이스
 *
 * 새로운 할인 유형 추가 시 이 인터페이스를 구현하는 새 클래스만 추가하면 됩니다 (OCP).
 */
interface DiscountPolicy {

    /**
     * 해당 쿠폰에 이 정책을 적용할 수 있는지 판단합니다.
     *
     * @param coupon 적용 가능 여부를 확인할 쿠폰
     * @return 적용 가능하면 true, 그렇지 않으면 false
     */
    fun supports(coupon: Coupon): Boolean

    /**
     * 주문 금액에 대한 할인 금액을 계산합니다.
     *
     * @param orderAmount 주문 금액
     * @param coupon 적용할 쿠폰
     * @return 계산된 할인 금액
     */
    fun calculate(orderAmount: Money, coupon: Coupon): Money
}
