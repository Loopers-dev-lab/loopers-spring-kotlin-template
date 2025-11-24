package com.loopers.domain.coupon

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class CouponTypeTest {

    @DisplayName("정액 쿠폰 타입은 할인 금액이 없으면 예외가 발생한다")
    @Test
    fun validateFixedAmountCoupon_failWhenInvalid() {
        val type = CouponType.FIXED_AMOUNT

        val exception = assertThrows<CoreException> {
            type.validate(discountAmount = null, discountRate = null)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_COUPON_DISCOUNT)
    }

    @DisplayName("정률 쿠폰 타입은 할인율이 범위를 벗어나면 예외가 발생한다")
    @Test
    fun validatePercentageCoupon_failWhenInvalid() {
        val type = CouponType.PERCENTAGE

        val exception = assertThrows<CoreException> {
            type.validate(discountAmount = null, discountRate = 101)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_COUPON_DISCOUNT)
    }

    @DisplayName("정액 쿠폰의 할인 금액이 주문 금액보다 크면 주문 금액을 반환한다")
    @Test
    fun calculateFixedAmountDiscount_cappedByOrderAmount() {
        val type = CouponType.FIXED_AMOUNT
        val orderAmount = Money.of(3000L)

        val discount = type.calculateDiscount(
            discountAmount = 5000L,
            discountRate = null,
            orderAmount = orderAmount
        )

        assertThat(discount.amount).isEqualTo(3000L)
    }

    @DisplayName("정률 쿠폰은 주문 금액의 할인율만큼 할인한다")
    @Test
    fun calculatePercentageDiscount() {
        val type = CouponType.PERCENTAGE
        val orderAmount = Money.of(50000L)

        val discount = type.calculateDiscount(
            discountAmount = null,
            discountRate = 10,
            orderAmount = orderAmount
        )

        assertThat(discount.amount).isEqualTo(5000L) // 50000 * 10% = 5000
    }
}
