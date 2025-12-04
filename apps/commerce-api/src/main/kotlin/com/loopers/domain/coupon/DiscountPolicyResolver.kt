package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 할인 정책 리졸버
 *
 * 쿠폰을 받아 적절한 할인 정책을 찾아 반환합니다.
 * 정책 선택 기준을 캡슐화하여 호출 측이 선택 로직을 알 필요 없습니다.
 */
@Component
class DiscountPolicyResolver(
    private val policies: List<DiscountPolicy>,
) {
    fun resolve(coupon: Coupon): DiscountPolicy {
        return policies.find { it.supports(coupon) }
            ?: throw CoreException(
                ErrorType.INTERNAL_ERROR,
                "지원하지 않는 할인 유형입니다: ${coupon.discountAmount.type}",
            )
    }
}
