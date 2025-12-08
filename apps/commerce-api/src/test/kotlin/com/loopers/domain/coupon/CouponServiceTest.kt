package com.loopers.domain.coupon

import com.loopers.IntegrationTest
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.infrastructure.coupon.CouponIssueJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.fixtures.CouponFixtures
import com.loopers.support.fixtures.UserFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CouponServiceTest : IntegrationTest() {

    @Autowired
    private lateinit var couponService: CouponService

    @Autowired
    private lateinit var couponJpaRepository: CouponJpaRepository

    @Autowired
    private lateinit var couponIssueJpaRepository: CouponIssueJpaRepository

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Nested
    @DisplayName("applyCoupon 메서드는")
    inner class ApplyCoupon {

        @Test
        fun `쿠폰 ID가 null이면 할인 금액 0을 반환한다`() {
            // given
            val user = createUser()
            val totalAmount = 10000L

            // when
            val discountAmount = couponService.calculateCouponDiscount(user.id, null, totalAmount)

            // then
            assertThat(discountAmount).isEqualTo(0L)
        }

        @Test
        fun `정액 쿠폰을 적용하면 할인 금액을 반환한다`() {
            // given
            val user = createUser()
            val coupon = createCoupon(
                name = "5000원 할인 쿠폰",
                discountType = DiscountType.FIXED,
                discountValue = 5000L,
            )
            createCouponIssue(user.id, coupon.id)
            val totalAmount = 10000L

            // when
            val discountAmount = couponService.calculateCouponDiscount(user.id, coupon.id, totalAmount)

            // then
            assertSoftly { softly ->
                softly.assertThat(discountAmount).isEqualTo(5000L)
            }
        }

        @Test
        fun `정률 쿠폰을 적용하면 할인 금액을 반환한다`() {
            // given
            val user = createUser()
            val coupon = createCoupon(
                name = "10% 할인 쿠폰",
                discountType = DiscountType.RATE,
                discountValue = 10L,
            )
            createCouponIssue(user.id, coupon.id)
            val totalAmount = 10000L

            // when
            val discountAmount = couponService.calculateCouponDiscount(user.id, coupon.id, totalAmount)

            // then
            assertSoftly { softly ->
                softly.assertThat(discountAmount).isEqualTo(1000L)
            }
        }

        @Test
        fun `쿠폰이 존재하지 않으면 예외가 발생한다`() {
            // given
            val user = createUser()
            val nonExistentCouponId = 999L
            val totalAmount = 10000L

            // when & then
            assertThatThrownBy {
                couponService.calculateCouponDiscount(user.id, nonExistentCouponId, totalAmount)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다")
        }

        @Test
        fun `사용자가 발급받지 않은 쿠폰이면 예외가 발생한다`() {
            // given
            val user = createUser()
            val otherUser = createUser("other123", "other@example.com")
            val coupon = createCoupon()
            createCouponIssue(otherUser.id, coupon.id) // 다른 사용자에게만 발급
            val totalAmount = 10000L

            // when & then
            assertThatThrownBy {
                couponService.calculateCouponDiscount(user.id, coupon.id, totalAmount)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("사용자가 발급 받은 적 없는 쿠폰입니다")
        }

        @Test
        fun `이미 사용된 쿠폰을 다시 사용하면 예외가 발생한다`() {
            // given
            val user = createUser()
            val coupon = createCoupon()
            createCouponIssue(user.id, coupon.id)

            // 첫 번째 사용
            couponService.applyCoupon(user.id, coupon.id)

            // when & then - 두 번째 사용 시도
            assertThatThrownBy {
                couponService.applyCoupon(user.id, coupon.id)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("이미 사용된 쿠폰입니다")
        }

        @Test
        fun `할인 금액이 주문 금액보다 크면 주문 금액만큼만 할인된다`() {
            // given
            val user = createUser()
            val coupon = createCoupon(
                name = "5000원 할인 쿠폰",
                discountType = DiscountType.FIXED,
                discountValue = 5000L,
            )
            createCouponIssue(user.id, coupon.id)
            val totalAmount = 3000L // 할인액보다 작은 주문 금액

            // when
            val discountAmount = couponService.calculateCouponDiscount(user.id, coupon.id, totalAmount)

            // then
            assertThat(discountAmount).isEqualTo(3000L) // 5000원이 아닌 3000원만 할인
        }

        @Test
        fun `여러 사용자가 같은 쿠폰을 발급받아 각각 사용할 수 있다`() {
            // given
            val user1 = createUser("user1", "user1@example.com")
            val user2 = createUser("user2", "user2@example.com")
            val coupon = createCoupon(
                discountType = DiscountType.FIXED,
                discountValue = 5000L,
            )
            val couponIssue1 = createCouponIssue(user1.id, coupon.id)
            val couponIssue2 = createCouponIssue(user2.id, coupon.id)
            val totalAmount = 10000L

            // when
            val discountAmount1 = couponService.calculateCouponDiscount(user1.id, coupon.id, totalAmount)
            val discountAmount2 = couponService.calculateCouponDiscount(user2.id, coupon.id, totalAmount)

            // then
            couponIssueJpaRepository.findById(couponIssue1.id!!).orElseThrow()
            couponIssueJpaRepository.findById(couponIssue2.id!!).orElseThrow()

            assertSoftly { softly ->
                softly.assertThat(discountAmount1).isEqualTo(5000L)
                softly.assertThat(discountAmount2).isEqualTo(5000L)
            }
        }
    }

    private fun createUser(
        userId: String = "user123",
        email: String = "test@example.com",
    ): User {
        val user = UserFixtures.createUser(
            userId = userId,
            email = email,
            birthDate = "1990-01-01",
            gender = Gender.MALE,
        )
        return userJpaRepository.save(user)
    }

    private fun createCoupon(
        name: String = "테스트 쿠폰_${System.nanoTime()}",
        discountType: DiscountType = DiscountType.FIXED,
        discountValue: Long = 5000L,
    ): Coupon {
        val coupon = CouponFixtures.createCoupon(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
        )
        return couponJpaRepository.save(coupon)
    }

    private fun createCouponIssue(userId: Long, couponId: Long): CouponIssue {
        val couponIssue = CouponFixtures.createCouponIssue(
            userId = userId,
            couponId = couponId,
        )
        return couponIssueJpaRepository.save(couponIssue)
    }
}
