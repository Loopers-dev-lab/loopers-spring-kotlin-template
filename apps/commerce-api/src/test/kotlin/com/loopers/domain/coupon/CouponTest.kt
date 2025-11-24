package com.loopers.domain.coupon

import com.loopers.domain.shared.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class CouponTest {

    @DisplayName("정액 쿠폰을 생성할 수 있다")
    @Test
    fun createFixedAmountCoupon() {
        val coupon = Coupon(
            name = "5000원 할인 쿠폰",
            description = "신규 회원 대상",
            couponType = CouponType.FIXED_AMOUNT,
            discountAmount = 5000L,
            discountRate = null,
        )

        assertAll(
            { assertThat(coupon.name).isEqualTo("5000원 할인 쿠폰") },
            { assertThat(coupon.couponType).isEqualTo(CouponType.FIXED_AMOUNT) },
            { assertThat(coupon.discountAmount).isEqualTo(5000L) },
            { assertThat(coupon.discountRate).isNull() }
        )
    }

    @DisplayName("정률 쿠폰을 생성할 수 있다")
    @Test
    fun createPercentageCoupon() {
        val coupon = Coupon(
            name = "10% 할인 쿠폰",
            description = "전 상품 10% 할인",
            couponType = CouponType.PERCENTAGE,
            discountAmount = null,
            discountRate = 10,
        )

        assertAll(
            { assertThat(coupon.name).isEqualTo("10% 할인 쿠폰") },
            { assertThat(coupon.couponType).isEqualTo(CouponType.PERCENTAGE) },
            { assertThat(coupon.discountAmount).isNull() },
            { assertThat(coupon.discountRate).isEqualTo(10) },
        )
    }

    @DisplayName("정액 쿠폰으로 할인 금액을 계산할 수 있다")
    @Test
    fun calculateDiscountWithFixedAmount() {
        val coupon = Coupon(
            name = "5000원 할인",
            description = null,
            couponType = CouponType.FIXED_AMOUNT,
            discountAmount = 5000L,
            discountRate = null,
        )
        val orderAmount = Money.of(50000L)

        val discount = coupon.calculateDiscount(orderAmount)

        assertThat(discount.amount).isEqualTo(5000L)
    }



    @DisplayName("정액 쿠폰 할인 금액이 주문 금액보다 크면 주문 금액만큼만 할인된다")
    @Test
    fun calculateDiscountWithFixedAmountExceedingOrderAmount() {
        val coupon = Coupon(
            name = "10000원 할인",
            description = null,
            couponType = CouponType.FIXED_AMOUNT,
            discountAmount = 10000L,
            discountRate = null,
        )
        val orderAmount = Money.of(5000L)

        val discount = coupon.calculateDiscount(orderAmount)

        assertThat(discount.amount).isEqualTo(5000L)
    }

    @DisplayName("정률 쿠폰으로 할인 금액을 계산할 수 있다")
    @Test
    fun calculateDiscountWithPercentage() {
        val coupon = Coupon(
            name = "10% 할인",
            description = null,
            couponType = CouponType.PERCENTAGE,
            discountAmount = null,
            discountRate = 10,
        )
        val orderAmount = Money.of(50000L)

        val discount = coupon.calculateDiscount(orderAmount)

        assertThat(discount.amount).isEqualTo(5000L)
    }

}
