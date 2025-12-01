package com.loopers.domain.coupon

import com.loopers.support.fixtures.CouponFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Coupon 도메인 테스트")
class CouponTest {

    @Nested
    @DisplayName("쿠폰 생성")
    inner class Create {

        @Test
        fun `정액 할인 쿠폰을 생성한다`() {
            // when
            val coupon = CouponFixtures.createCoupon(
                name = "5000원 할인 쿠폰",
                discountType = DiscountType.FIXED,
                discountValue = 5000L,
            )

            // then
            assertSoftly {
                it.assertThat(coupon.name).isEqualTo("5000원 할인 쿠폰")
                it.assertThat(coupon.discountType).isEqualTo(DiscountType.FIXED)
                it.assertThat(coupon.discountValue).isEqualTo(5000L)
            }
        }

        @Test
        fun `정률 할인 쿠폰을 생성한다`() {
            // when
            val coupon = CouponFixtures.createCoupon(
                name = "10% 할인 쿠폰",
                discountType = DiscountType.RATE,
                discountValue = 10L,
            )

            // then
            assertSoftly {
                it.assertThat(coupon.name).isEqualTo("10% 할인 쿠폰")
                it.assertThat(coupon.discountType).isEqualTo(DiscountType.RATE)
                it.assertThat(coupon.discountValue).isEqualTo(10L)
            }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "  "])
        fun `쿠폰명이 비어있으면 예외가 발생한다`(name: String) {
            // when & then
            assertThatThrownBy {
                Coupon.create(
                    name = name,
                    discountType = DiscountType.FIXED,
                    discountValue = 5000L,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("쿠폰명은 필수입니다")
        }

        @ParameterizedTest
        @ValueSource(longs = [0, -1, 101, 150])
        fun `정률 쿠폰의 할인율이 1에서 100 사이가 아니면 예외가 발생한다`(discountValue: Long) {
            // when & then
            assertThatThrownBy {
                Coupon.create(
                    name = "할인율 테스트 쿠폰",
                    discountType = DiscountType.RATE,
                    discountValue = discountValue,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("할인율은 1~100 사이여야 합니다")
        }
    }

    @Nested
    @DisplayName("할인 금액 계산")
    inner class CalculateDiscount {

        @ParameterizedTest
        @CsvSource(
            // 할인액이 주문금액보다 작음 → 할인액만큼 할인
            "5000, 10000, 5000",
            // 할인액이 주문금액보다 큼 → 주문금액만큼만 할인 (음수 방지)
            "5000, 3000, 3000",
            // 할인액과 주문금액 동일 → 전액 할인 (0원)
            "5000, 5000, 5000",
        )
        fun `정액 쿠폰의 할인 금액을 계산한다`(
            discountValue: Long,
            totalAmount: Long,
            expectedDiscount: Long,
        ) {
            // given
            val coupon = CouponFixtures.createCoupon(
                discountType = DiscountType.FIXED,
                discountValue = discountValue,
            )

            // when
            val discount = coupon.calculateDiscount(totalAmount)

            // then
            assertThat(discount).isEqualTo(expectedDiscount)
        }

        @ParameterizedTest
        @CsvSource(
            "10, 10000, 1000",
            "20, 50000, 10000",
            "50, 20000, 10000",
            "100, 10000, 10000",
        )
        fun `정률 쿠폰의 할인 금액을 계산한다`(
            discountValue: Long,
            totalAmount: Long,
            expectedDiscount: Long,
        ) {
            // given
            val coupon = CouponFixtures.createCoupon(
                discountType = DiscountType.RATE,
                discountValue = discountValue,
            )

            // when
            val discount = coupon.calculateDiscount(totalAmount)

            // then
            assertThat(discount).isEqualTo(expectedDiscount)
        }
    }
}
