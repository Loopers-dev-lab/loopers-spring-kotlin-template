package com.loopers.domain.coupon

import com.loopers.domain.order.OrderItemCommand
import com.loopers.domain.product.Product
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
     * 주문에 대한 쿠폰 할인 계산 및 사용 처리
     * 쿠폰이 없으면 할인 없음(zero), 있으면 할인 계산 후 쿠폰 사용 처리
     */
    @Transactional
    fun applyAndUseCouponForOrder(
        memberId: String,
        couponId: Long?,
        orderItems: List<OrderItemCommand>,
        productMap: Map<Long, Product>
    ): Money {
        // 쿠폰이 없으면 할인 없음
        if (couponId == null) {
            return Money.zero()
        }

        // 쿠폰 조회 및 검증 (비관적 락 적용 - 쿠폰 동시성 제어)
        val memberCoupon = getMemberCoupon(memberId, couponId)
            ?: throw IllegalArgumentException("쿠폰을 찾을 수 없습니다")

        // 총 주문 금액 계산
        val totalAmount = orderItems.sumOf { item ->
            val product = productMap[item.productId]
                ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: ${item.productId}")
            product.price.amount * item.quantity
        }.let { Money(it) }

        // 할인 금액 계산
        val discountAmount = calculateDiscount(memberCoupon, totalAmount)

        // 쿠폰 사용 처리
        memberCoupon.use()

        return discountAmount
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
