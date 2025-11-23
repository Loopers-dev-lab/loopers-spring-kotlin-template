package com.loopers.domain.coupon

import com.loopers.IntegrationTestSupport
import com.loopers.domain.user.UserFixture
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture

class CouponServiceTest(
    private val couponService: CouponService,
    private val couponRepository: CouponJpaRepository,
    private val userRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("정액 할인 쿠폰 사용")
    @Nested
    inner class FixedAmountDiscount {

        @DisplayName("정액 할인 쿠폰으로 할인가 계산이 성공한다.")
        @Test
        fun calculateDiscountPrice_withFixedAmountCoupon() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val coupon = CouponModel.create(
                refUserId = user.id,
                name = "5000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal.valueOf(5000),
            )
            couponRepository.save(coupon)

            val totalPrice = BigDecimal.valueOf(10000)

            // act
            val discountPrice = couponService.calculateDiscountPrice(coupon.id, user.id, totalPrice)

            // assert
            val updatedCoupon = couponRepository.findById(coupon.id).get()
            assertAll(
                { assertThat(discountPrice).isEqualByComparingTo(BigDecimal.valueOf(5000)) },
                { assertThat(updatedCoupon.isUsed).isTrue() },
            )
        }

        @DisplayName("할인 금액이 주문 금액보다 큰 경우 예외가 발생한다.")
        @Test
        fun calculateDiscountPriceFails_whenDiscountExceedsOrderAmount() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val coupon = CouponModel.create(
                refUserId = user.id,
                name = "10000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal.valueOf(10000),
            )
            couponRepository.save(coupon)

            val totalPrice = BigDecimal.valueOf(5000) // 할인액보다 작음

            // act & assert
            val exception = assertThrows<CoreException> {
                couponService.calculateDiscountPrice(coupon.id, user.id, totalPrice)
            }

            assertThat(exception.message).isEqualTo("할인 금액이 주문보다 클 수 없습니다.")
        }
    }

    @DisplayName("정률 할인 쿠폰 사용")
    @Nested
    inner class PercentageDiscount {

        @DisplayName("정률 할인 쿠폰으로 할인가 계산이 성공한다.")
        @Test
        fun calculateDiscountPrice_withPercentageCoupon() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val coupon = CouponModel.create(
                refUserId = user.id,
                name = "10% 할인 쿠폰",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal.valueOf(10),
            )
            couponRepository.save(coupon)

            val totalPrice = BigDecimal.valueOf(10000)

            // act
            val discountPrice = couponService.calculateDiscountPrice(coupon.id, user.id, totalPrice)

            // assert
            val updatedCoupon = couponRepository.findById(coupon.id).get()
            assertAll(
                { assertThat(discountPrice).isEqualByComparingTo(BigDecimal.valueOf(1000)) },
                { assertThat(updatedCoupon.isUsed).isTrue() },
            )
        }

        @DisplayName("50% 할인 쿠폰으로 할인가 계산이 성공한다.")
        @Test
        fun calculateDiscountPrice_with50PercentCoupon() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val coupon = CouponModel.create(
                refUserId = user.id,
                name = "50% 할인 쿠폰",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal.valueOf(50),
            )
            couponRepository.save(coupon)

            val totalPrice = BigDecimal.valueOf(20000)

            // act
            val discountPrice = couponService.calculateDiscountPrice(coupon.id, user.id, totalPrice)

            // assert
            assertAll(
                { assertThat(discountPrice).isEqualByComparingTo(BigDecimal.valueOf(10000)) },
            )
        }
    }

    @DisplayName("쿠폰 사용 실패 케이스")
    @Nested
    inner class CouponUsageFailure {

        @DisplayName("존재하지 않는 쿠폰으로 할인 시도 시 실패한다.")
        @Test
        fun calculateDiscountPriceFails_whenCouponNotExists() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val nonExistentCouponId = 999L
            val totalPrice = BigDecimal.valueOf(10000)

            // act & assert
            val exception = assertThrows<CoreException> {
                couponService.calculateDiscountPrice(nonExistentCouponId, user.id, totalPrice)
            }

            assertThat(exception.message).isEqualTo("사용 가능한 쿠폰이 존재하지 않습니다.")
        }

        @DisplayName("이미 사용된 쿠폰으로 할인 시도 시 실패한다.")
        @Test
        fun calculateDiscountPriceFails_whenCouponAlreadyUsed() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val coupon = CouponModel.create(
                refUserId = user.id,
                name = "1000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal.valueOf(1000),
            )
            couponRepository.save(coupon)

            val totalPrice = BigDecimal.valueOf(10000)

            // 첫 번째 사용
            couponService.calculateDiscountPrice(coupon.id, user.id, totalPrice)

            // act & assert - 두 번째 사용 시도
            val exception = assertThrows<CoreException> {
                couponService.calculateDiscountPrice(coupon.id, user.id, totalPrice)
            }

            assertThat(exception.message).isEqualTo("사용 가능한 쿠폰이 존재하지 않습니다.")
        }
    }

    @DisplayName("동시성 제어")
    @Nested
    inner class ConcurrencyControl {

        @DisplayName("동시에 같은 쿠폰 사용 시도 시 한 번만 성공한다.")
        @Test
        fun onlyOneSucceeds_whenConcurrentCouponUsage() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val coupon = CouponModel.create(
                refUserId = user.id,
                name = "5000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal.valueOf(5000),
            )
            couponRepository.save(coupon)

            val totalPrice = BigDecimal.valueOf(10000)

            // act - 동시에 2번 사용 시도
            val futures = (1..2).map {
                CompletableFuture.supplyAsync {
                    try {
                        couponService.calculateDiscountPrice(coupon.id, user.id, totalPrice)
                        true // 성공
                    } catch (e: Exception) {
                        false // 실패
                    }
                }
            }

            val results = futures.map { it.join() }
            val successCount = results.count { it }
            val updatedCoupon = couponRepository.findById(coupon.id).get()

            // assert
            assertAll(
                { assertThat(successCount).isEqualTo(1) },
                { assertThat(updatedCoupon.isUsed).isTrue() },
            )
        }

        @DisplayName("서로 다른 쿠폰은 동시에 사용 가능하다.")
        @Test
        fun bothSucceed_whenDifferentCouponsUsedConcurrently() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val coupon1 = CouponModel.create(
                refUserId = user.id,
                name = "쿠폰 1",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal.valueOf(1000),
            )
            couponRepository.save(coupon1)

            val coupon2 = CouponModel.create(
                refUserId = user.id,
                name = "쿠폰 2",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal.valueOf(2000),
            )
            couponRepository.save(coupon2)

            val totalPrice = BigDecimal.valueOf(10000)

            // act - 동시에 서로 다른 쿠폰 사용
            val futures = listOf(
                CompletableFuture.supplyAsync {
                    try {
                        couponService.calculateDiscountPrice(coupon1.id, user.id, totalPrice)
                        true
                    } catch (e: Exception) {
                        false
                    }
                },
                CompletableFuture.supplyAsync {
                    try {
                        couponService.calculateDiscountPrice(coupon2.id, user.id, totalPrice)
                        true
                    } catch (e: Exception) {
                        false
                    }
                },
            )

            val results = futures.map { it.join() }
            val successCount = results.count { it }

            val updatedCoupon1 = couponRepository.findById(coupon1.id).get()
            val updatedCoupon2 = couponRepository.findById(coupon2.id).get()

            // assert
            assertAll(
                // 둘 다 성공
                { assertThat(successCount).isEqualTo(2) },
                { assertThat(updatedCoupon1.isUsed).isTrue() },
                { assertThat(updatedCoupon2.isUsed).isTrue() },
            )
        }
    }
}
