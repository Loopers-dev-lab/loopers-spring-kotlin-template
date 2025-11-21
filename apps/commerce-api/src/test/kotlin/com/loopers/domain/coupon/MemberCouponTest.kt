package com.loopers.domain.coupon

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class MemberCouponTest {

    @DisplayName("회원 쿠폰을 발급할 수 있다")
    @Test
    fun issueMemberCoupon() {
        val coupon = Coupon(
            name = "5000원 할인",
            description = null,
            couponType = CouponType.FIXED_AMOUNT,
            discountAmount = 5000L,
            discountRate = null,
        )

        val memberCoupon = MemberCoupon.issue("member123", coupon)

        assertAll(
            { assertThat(memberCoupon.memberId).isEqualTo("member123") },
            { assertThat(memberCoupon.coupon).isEqualTo(coupon) },
            { assertThat(memberCoupon.usedAt).isNull() },
            { assertThat(memberCoupon.canUse()).isTrue() },
        )
    }

    @DisplayName("미사용 쿠폰은 사용 가능하다")
    @Test
    fun canUseUnusedCoupon() {
        val coupon = Coupon(
            name = "5000원 할인",
            description = null,
            couponType = CouponType.FIXED_AMOUNT,
            discountAmount = 5000L,
            discountRate = null,
        )

        val memberCoupon = MemberCoupon.issue("member123", coupon)

        val canUse = memberCoupon.canUse()

        assertThat(canUse).isTrue()
    }

    @DisplayName("쿠폰을 사용할 수 있다")
    @Test
    fun useCoupon() {
        val coupon = Coupon(
            name = "5000원 할인",
            description = null,
            couponType = CouponType.FIXED_AMOUNT,
            discountAmount = 5000L,
            discountRate = null,
        )

        val memberCoupon = MemberCoupon.issue("member123", coupon)

        memberCoupon.use()

        assertAll(
            { assertThat(memberCoupon.usedAt).isNotNull() },
            { assertThat(memberCoupon.canUse()).isFalse() },
        )
    }

    @DisplayName("이미 사용한 쿠폰은 다시 사용할 수 없다")
    @Test
    fun failToReuseUsedCoupon() {
        val coupon = Coupon(
            name = "5000원 할인",
            description = null,
            couponType = CouponType.FIXED_AMOUNT,
            discountAmount = 5000L,
            discountRate = null,
        )

        val memberCoupon = MemberCoupon.issue("member123", coupon)
        memberCoupon.use()

        val exception = assertThrows<CoreException> {
            memberCoupon.use()
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_ALREADY_USED)
    }

    @DisplayName("회원 쿠폰으로 할인 금액을 계산할 수 있다")
    @Test
    fun calculateDiscount() {
        val coupon = Coupon(
            name = "10% 할인",
            description = null,
            couponType = CouponType.PERCENTAGE,
            discountAmount = null,
            discountRate = 10,
        )

        val memberCoupon = MemberCoupon.issue("member123", coupon)
        val orderAmount = Money.of(50000L)

        val discount = memberCoupon.calculateDiscount(orderAmount)

        assertThat(discount.amount).isEqualTo(5000L)

    }
}
