package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DiscountPolicyResolver 단위 테스트")
class DiscountPolicyResolverTest {

    @DisplayName("resolve")
    @Nested
    inner class Resolve {

        private val fixedAmountPolicy = FixedAmountPolicy()
        private val ratePolicy = RatePolicy()
        private val resolver = DiscountPolicyResolver(listOf(fixedAmountPolicy, ratePolicy))

        @DisplayName("FIXED_AMOUNT 타입 쿠폰에 대해 FixedAmountPolicy를 반환한다")
        @Test
        fun `resolve returns FixedAmountPolicy for FIXED_AMOUNT type`() {
            // given
            val coupon = createCoupon(DiscountType.FIXED_AMOUNT, 5000)

            // when
            val result = resolver.resolve(coupon)

            // then
            assertThat(result).isInstanceOf(FixedAmountPolicy::class.java)
        }

        @DisplayName("RATE 타입 쿠폰에 대해 RatePolicy를 반환한다")
        @Test
        fun `resolve returns RatePolicy for RATE type`() {
            // given
            val coupon = createCoupon(DiscountType.RATE, 10)

            // when
            val result = resolver.resolve(coupon)

            // then
            assertThat(result).isInstanceOf(RatePolicy::class.java)
        }

        @DisplayName("지원하는 정책이 없으면 CoreException을 발생시킨다")
        @Test
        fun `resolve throws CoreException when no policy supports the coupon`() {
            // given
            val emptyResolver = DiscountPolicyResolver(emptyList())
            val coupon = createCoupon(DiscountType.FIXED_AMOUNT, 5000)

            // when & then
            assertThatThrownBy { emptyResolver.resolve(coupon) }
                .isInstanceOf(CoreException::class.java)
                .extracting("errorType")
                .isEqualTo(ErrorType.INTERNAL_ERROR)
        }

        private fun createCoupon(type: DiscountType, value: Long): Coupon {
            return Coupon.of(
                name = "테스트 쿠폰",
                discountAmount = DiscountAmount(type = type, value = value),
            )
        }
    }
}
