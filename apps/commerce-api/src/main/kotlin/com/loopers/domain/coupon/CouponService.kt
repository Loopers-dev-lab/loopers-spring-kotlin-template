package com.loopers.domain.coupon

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class CouponService(
    private val couponRepository: CouponRepository,
    private val memberCouponRepository: MemberCouponRepository
) {

    /**
     * 회원의 특정 쿠폰 조회 (주문 시 사용)
     * 비관적 락 적용으로 동시성 제어
     */
    fun getMemberCoupon(memberId: String, couponId: Long): MemberCoupon? {
        // 쿠폰 존재 여부 확인
        couponRepository.findByIdOrThrow(couponId)

        // 회원의 쿠폰 조회 (비관적 락)
        return memberCouponRepository.findByMemberIdAndCouponIdWithLock(memberId, couponId)
            ?: throw CoreException(
                ErrorType.COUPON_NOT_FOUND,
                "회원이 해당 쿠폰을 보유하고 있지 않습니다. 회원: $memberId, 쿠폰: $couponId"
            )
    }


    /**
     * 쿠폰 할인 금액 계산
     */
    fun calculateDiscount(memberCoupon: MemberCoupon, orderAmount: Money): Money {
        // 사용 가능한지 확인
        if (!memberCoupon.canUse()) {
            throw CoreException(
                ErrorType.COUPON_NOT_AVAILABLE,
                "사용할 수 없는 쿠폰입니다."
            )
        }
        return memberCoupon.calculateDiscount(orderAmount)
    }

    /**
     * 쿠폰 사용 처리 (주문 완료 시 호출)
     */
    @Transactional
    fun useCoupon(memberCoupon: MemberCoupon) {
        memberCoupon.use()
    }
}
