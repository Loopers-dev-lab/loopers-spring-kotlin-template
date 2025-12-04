package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.test.util.ReflectionTestUtils

@DisplayName("Coupon 단위 테스트")
class CouponTest {

    @DisplayName("쿠폰 발급")
    @Nested
    inner class IssueTest {

        @DisplayName("쿠폰을 사용자에게 발급하면 IssuedCoupon이 생성된다")
        @Test
        fun `issue coupon to user creates IssuedCoupon`() {
            // given
            val coupon = createCoupon(couponId = 100L)
            val userId = 1L

            // when
            val issuedCoupon = coupon.issue(userId)

            // then
            assertAll(
                { assertThat(issuedCoupon.userId).isEqualTo(userId) },
                { assertThat(issuedCoupon.couponId).isEqualTo(coupon.id) },
                { assertThat(issuedCoupon.status).isEqualTo(UsageStatus.AVAILABLE) },
                { assertThat(issuedCoupon.usedAt).isNull() },
            )
        }

        @DisplayName("동일한 쿠폰을 여러 사용자에게 발급할 수 있다")
        @Test
        fun `issue same coupon to multiple users`() {
            // given
            val coupon = createCoupon(couponId = 100L)
            val userId1 = 1L
            val userId2 = 2L

            // when
            val issuedCoupon1 = coupon.issue(userId1)
            val issuedCoupon2 = coupon.issue(userId2)

            // then
            assertAll(
                { assertThat(issuedCoupon1.userId).isEqualTo(userId1) },
                { assertThat(issuedCoupon1.couponId).isEqualTo(coupon.id) },
                { assertThat(issuedCoupon2.userId).isEqualTo(userId2) },
                { assertThat(issuedCoupon2.couponId).isEqualTo(coupon.id) },
            )
        }
    }

    private fun createCoupon(
        couponId: Long = 1L,
        name: String = "테스트 쿠폰",
        discountAmount: DiscountAmount = DiscountAmount(DiscountType.FIXED_AMOUNT, 1000),
    ): Coupon {
        val coupon = Coupon.of(name, discountAmount)
        ReflectionTestUtils.setField(coupon, "id", couponId)
        return coupon
    }
}
