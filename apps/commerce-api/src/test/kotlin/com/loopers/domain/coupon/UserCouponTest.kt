package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("UserCoupon 도메인 테스트")
class UserCouponTest {

    @Test
    @DisplayName("사용자 쿠폰을 생성할 수 있다")
    fun createUserCoupon() {
        // given
        val userId = 1L
        val coupon = createFixedAmountCoupon()

        // when
        val userCoupon = UserCoupon(
            userId = userId,
            coupon = coupon,
        )

        // then
        assertThat(userCoupon.userId).isEqualTo(userId)
        assertThat(userCoupon.coupon).isEqualTo(coupon)
        assertThat(userCoupon.isUsed).isFalse()
        assertThat(userCoupon.canUse()).isTrue()
    }

    @Test
    @DisplayName("쿠폰을 사용하면 isUsed가 true가 된다")
    fun useCoupon_shouldSetIsUsedToTrue() {
        // given
        val userCoupon = createUserCoupon(isUsed = false)

        // when
        userCoupon.use()

        // then
        assertThat(userCoupon.isUsed).isTrue()
        assertThat(userCoupon.canUse()).isFalse()
    }

    @Test
    @DisplayName("이미 사용한 쿠폰은 다시 사용할 수 없다")
    fun usedCoupon_cannotBeUsedAgain() {
        // given
        val userCoupon = createUserCoupon(isUsed = true)

        // when & then
        assertThatThrownBy {
            userCoupon.use()
        }.hasMessageContaining("이미 사용된 쿠폰입니다")
    }

    @Test
    @DisplayName("사용하지 않은 쿠폰만 사용 가능하다")
    fun canUse_shouldReturnTrueOnlyForUnusedCoupon() {
        // given
        val unusedCoupon = createUserCoupon(isUsed = false)
        val usedCoupon = createUserCoupon(isUsed = true)

        // then
        assertThat(unusedCoupon.canUse()).isTrue()
        assertThat(usedCoupon.canUse()).isFalse()
    }

    private fun createFixedAmountCoupon(): Coupon = Coupon(
            name = "5000원 할인 쿠폰",
            discountType = CouponType.FIXED_AMOUNT,
            discountValue = BigDecimal("5000"),
        )

    private fun createUserCoupon(isUsed: Boolean = false): UserCoupon {
        val userCoupon = UserCoupon(
            userId = 1L,
            coupon = createFixedAmountCoupon(),
        )
        // isUsed를 설정하기 위해 필요시 use() 호출
        if (isUsed) {
            userCoupon.use()
        }
        return userCoupon
    }
}
