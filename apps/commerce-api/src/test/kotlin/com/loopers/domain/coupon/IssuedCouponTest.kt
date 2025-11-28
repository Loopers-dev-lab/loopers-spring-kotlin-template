package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import java.time.ZonedDateTime

@DisplayName("IssuedCoupon 단위 테스트")
class IssuedCouponTest {

    companion object {
        private val FIXED_TIME = ZonedDateTime.parse("2025-01-15T10:00:00+09:00[Asia/Seoul]")
    }

    @DisplayName("쿠폰 발급")
    @Nested
    inner class IssueTest {

        @DisplayName("쿠폰을 발급하면 AVAILABLE 상태로 생성된다")
        @Test
        fun `create issued coupon with AVAILABLE status`() {
            // given
            val userId = 1L
            val coupon = createCoupon(couponId = 100L)

            // when
            val issuedCoupon = IssuedCoupon.issue(userId, coupon)

            // then
            assertAll(
                { assertThat(issuedCoupon.userId).isEqualTo(userId) },
                { assertThat(issuedCoupon.couponId).isEqualTo(coupon.id) },
                { assertThat(issuedCoupon.status).isEqualTo(UsageStatus.AVAILABLE) },
                { assertThat(issuedCoupon.usedAt).isNull() },
            )
        }
    }

    @DisplayName("쿠폰 사용")
    @Nested
    inner class UseTest {

        @DisplayName("사용 가능한 쿠폰을 사용하면 USED 상태로 변경되고 할인 금액을 반환한다")
        @Test
        fun `use available coupon successfully`() {
            // given
            val userId = 1L
            val coupon = createCoupon(discountValue = 5000)
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val orderAmount = Money.krw(10000)

            // when
            val discountAmount = issuedCoupon.use(userId, coupon, orderAmount, FIXED_TIME)

            // then
            assertAll(
                { assertThat(discountAmount).isEqualTo(Money.krw(5000)) },
                { assertThat(issuedCoupon.status).isEqualTo(UsageStatus.USED) },
                { assertThat(issuedCoupon.usedAt).isNotNull() },
            )
        }

        @DisplayName("정률 할인 쿠폰을 사용하면 올바른 할인 금액을 반환한다")
        @Test
        fun `use rate coupon and return correct discount amount`() {
            // given
            val userId = 1L
            val coupon = createCoupon(
                discountType = DiscountType.RATE,
                discountValue = 10,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val orderAmount = Money.krw(50000)

            // when
            val discountAmount = issuedCoupon.use(userId, coupon, orderAmount, FIXED_TIME)

            // then
            assertThat(discountAmount).isEqualTo(Money.krw(5000))
        }

        @DisplayName("이미 사용된 쿠폰을 사용하면 예외가 발생한다")
        @Test
        fun `throw exception when using already used coupon`() {
            // given
            val userId = 1L
            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 쿠폰을 먼저 사용
            issuedCoupon.use(userId, coupon, Money.krw(10000), FIXED_TIME)

            // when
            val exception = assertThrows<CoreException> {
                issuedCoupon.use(userId, coupon, Money.krw(10000), FIXED_TIME)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("이미 사용된 쿠폰입니다")
        }

        @DisplayName("다른 쿠폰 정보로 사용하려고 하면 예외가 발생한다")
        @Test
        fun `throw exception when using different coupon`() {
            // given
            val userId = 1L
            val coupon = createCoupon(couponId = 100L)
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val differentCoupon = createCoupon(couponId = 200L)

            // when & then
            val exception = assertThrows<CoreException> {
                issuedCoupon.use(userId, differentCoupon, Money.krw(10000), FIXED_TIME)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("쿠폰 정보가 일치하지 않습니다")
        }
    }

    @DisplayName("할인 금액 계산")
    @Nested
    inner class DiscountCalculation {

        @DisplayName("주문 금액보다 큰 할인 금액은 주문 금액으로 제한된다")
        @Test
        fun `discount amount cannot exceed order amount`() {
            // given
            val userId = 1L
            val coupon = createCoupon(discountValue = 20000)
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val orderAmount = Money.krw(10000)

            // when
            val discountAmount = issuedCoupon.use(userId, coupon, orderAmount, FIXED_TIME)

            // then
            assertThat(discountAmount).isEqualTo(Money.krw(10000))
        }

        @DisplayName("100% 정률 할인 쿠폰은 전액 할인된다")
        @Test
        fun `100 percent rate coupon discounts full amount`() {
            // given
            val userId = 1L
            val coupon = createCoupon(
                discountType = DiscountType.RATE,
                discountValue = 100,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val orderAmount = Money.krw(25000)

            // when
            val discountAmount = issuedCoupon.use(userId, coupon, orderAmount, FIXED_TIME)

            // then
            assertThat(discountAmount).isEqualTo(Money.krw(25000))
        }
    }

    private fun createIssuedCoupon(
        userId: Long = 1L,
        coupon: Coupon = createCoupon(),
    ): IssuedCoupon {
        return IssuedCoupon.issue(userId, coupon)
    }

    private fun createCoupon(
        couponId: Long = 100L,
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: Long = 5000,
    ): Coupon {
        val coupon = Coupon.of(
            name = "테스트 쿠폰",
            discountAmount = DiscountAmount(
                type = discountType,
                value = discountValue,
            ),
        )
        ReflectionTestUtils.setField(coupon, "id", couponId)
        return coupon
    }
}
