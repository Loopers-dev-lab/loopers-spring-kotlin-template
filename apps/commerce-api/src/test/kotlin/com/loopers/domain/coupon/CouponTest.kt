package com.loopers.domain.coupon

import com.loopers.domain.order.Money
import com.loopers.domain.product.Currency
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Coupon 도메인 테스트")
class CouponTest {

    @Test
    @DisplayName("정액 쿠폰은 고정 금액을 할인한다")
    fun fixedAmountCoupon_shouldDiscountFixedAmount() {
        // given
        val coupon = Coupon(
            name = "5000원 할인 쿠폰",
            discountType = CouponType.FIXED_AMOUNT,
            discountValue = BigDecimal("5000"),
        )
        val orderAmount = Money(BigDecimal("20000"), Currency.KRW)

        // when
        val discountedAmount = coupon.calculateDiscount(orderAmount)

        // then
        assertThat(discountedAmount.amount).isEqualByComparingTo(BigDecimal("5000"))
    }

    @Test
    @DisplayName("정률 쿠폰은 비율에 따라 할인한다")
    fun percentageCoupon_shouldDiscountByPercentage() {
        // given
        val coupon = Coupon(
            name = "10% 할인 쿠폰",
            discountType = CouponType.PERCENTAGE,
            discountValue = BigDecimal("10"),
        )
        val orderAmount = Money(BigDecimal("20000"), Currency.KRW)

        // when
        val discountedAmount = coupon.calculateDiscount(orderAmount)

        // then
        assertThat(discountedAmount.amount).isEqualByComparingTo(BigDecimal("2000"))
    }

    @Test
    @DisplayName("정액 쿠폰 할인 금액이 주문 금액보다 크면 주문 금액만큼만 할인한다")
    fun fixedAmountCoupon_shouldNotExceedOrderAmount() {
        // given
        val coupon = Coupon(
            name = "10000원 할인 쿠폰",
            discountType = CouponType.FIXED_AMOUNT,
            discountValue = BigDecimal("10000"),
        )
        val orderAmount = Money(BigDecimal("5000"), Currency.KRW)

        // when
        val discountedAmount = coupon.calculateDiscount(orderAmount)

        // then
        assertThat(discountedAmount.amount).isEqualByComparingTo(BigDecimal("5000"))
    }

    @Test
    @DisplayName("쿠폰 할인 값은 0보다 커야 한다")
    fun coupon_discountValueShouldBePositive() {
        assertThatThrownBy {
            Coupon(
                name = "잘못된 쿠폰",
                discountType = CouponType.FIXED_AMOUNT,
                discountValue = BigDecimal("-1000"),
            )
        }.hasMessageContaining("할인 값은 0보다 커야 합니다")
    }

    @Test
    @DisplayName("정률 쿠폰의 할인율은 100을 초과할 수 없다")
    fun percentageCoupon_shouldNotExceed100Percent() {
        assertThatThrownBy {
            Coupon(
                name = "잘못된 정률 쿠폰",
                discountType = CouponType.PERCENTAGE,
                discountValue = BigDecimal("150"),
            )
        }.hasMessageContaining("정률 쿠폰의 할인율은 100을 초과할 수 없습니다")
    }
}
