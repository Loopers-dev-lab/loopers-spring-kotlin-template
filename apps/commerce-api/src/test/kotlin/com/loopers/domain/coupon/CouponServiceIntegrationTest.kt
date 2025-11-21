package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CouponServiceIntegrationTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("쿠폰 발급 통합테스트")
    @Nested
    inner class IssueCoupon {

        @DisplayName("쿠폰을 정상적으로 발급할 수 있다")
        @Test
        fun `issue coupon successfully`() {
            // given
            val userId = 1L
            val coupon = createCoupon()

            // when
            val issuedCoupon = couponService.issueCoupon(userId, coupon.id)

            // then
            assertAll(
                { assertThat(issuedCoupon.userId).isEqualTo(userId) },
                { assertThat(issuedCoupon.couponId).isEqualTo(coupon.id) },
                { assertThat(issuedCoupon.status).isEqualTo(UsageStatus.AVAILABLE) },
                { assertThat(issuedCoupon.usedAt).isNull() },
            )
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하려고 하면 예외가 발생한다")
        @Test
        fun `throw exception when coupon does not exist`() {
            // given
            val userId = 1L
            val nonExistentCouponId = 999L

            // when
            val exception = assertThrows<CoreException> {
                couponService.issueCoupon(userId, nonExistentCouponId)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("존재하지 않는 쿠폰입니다")
        }

        @DisplayName("이미 발급된 쿠폰을 중복 발급하려고 하면 예외가 발생한다")
        @Test
        fun `throw exception when trying to issue duplicate coupon`() {
            // given
            val userId = 1L
            val coupon = createCoupon()
            createIssuedCoupon(userId = userId, coupon = coupon)

            // when
            val exception = assertThrows<CoreException> {
                couponService.issueCoupon(userId, coupon.id)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(exception.message).contains("이미 발급된 쿠폰입니다")
        }
    }

    @DisplayName("보유 쿠폰 조회 (페이지네이션) 통합테스트")
    @Nested
    inner class FindUserCoupons {

        @DisplayName("사용된 쿠폰도 페이지네이션 결과에 포함된다")
        @Test
        fun `include used coupons in paginated results`() {
            // given
            val userId = 1L
            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 쿠폰 사용
            val couponEntity = couponRepository.findById(coupon.id)!!
            issuedCoupon.use(couponEntity, Money.krw(10000))
            issuedCouponRepository.save(issuedCoupon)

            // when
            val command = IssuedCouponCommand.FindUserCoupons(userId = userId)
            val result = couponService.findUserCoupons(command)

            // then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].issuedCoupon.status).isEqualTo(UsageStatus.USED)
            assertThat(result.hasNext()).isFalse()
        }

        @DisplayName("페이지네이션이 정상 동작한다")
        @Test
        fun `pagination works correctly`() {
            // given
            val userId = 1L
            val coupons = (1..5).map {
                createCoupon(name = "쿠폰 $it")
            }
            coupons.forEach { coupon ->
                createIssuedCoupon(userId = userId, coupon = coupon)
            }

            // when - 첫 페이지 (size=2)
            val firstPage = couponService.findUserCoupons(
                IssuedCouponCommand.FindUserCoupons(userId = userId, page = 0, size = 2),
            )

            // then
            assertAll(
                { assertThat(firstPage.content).hasSize(2) },
                { assertThat(firstPage.hasNext()).isTrue() },
            )

            // when - 두 번째 페이지
            val secondPage = couponService.findUserCoupons(
                IssuedCouponCommand.FindUserCoupons(userId = userId, page = 1, size = 2),
            )

            // then
            assertAll(
                { assertThat(secondPage.content).hasSize(2) },
                { assertThat(secondPage.hasNext()).isTrue() },
            )

            // when - 마지막 페이지
            val lastPage = couponService.findUserCoupons(
                IssuedCouponCommand.FindUserCoupons(userId = userId, page = 2, size = 2),
            )

            // then
            assertAll(
                { assertThat(lastPage.content).hasSize(1) },
                { assertThat(lastPage.hasNext()).isFalse() },
            )
        }

        @DisplayName("정렬 옵션이 정상 동작한다")
        @Test
        fun `sorting works correctly`() {
            // given
            val userId = 1L
            val coupon1 = createCoupon(name = "쿠폰 1")
            val coupon2 = createCoupon(name = "쿠폰 2")
            val coupon3 = createCoupon(name = "쿠폰 3")
            val issuedCoupon1 = createIssuedCoupon(userId = userId, coupon = coupon1)
            val issuedCoupon2 = createIssuedCoupon(userId = userId, coupon = coupon2)
            val issuedCoupon3 = createIssuedCoupon(userId = userId, coupon = coupon3)

            // when - LATEST 정렬
            val latestResult = couponService.findUserCoupons(
                IssuedCouponCommand.FindUserCoupons(userId = userId, sort = IssuedCouponSortType.LATEST),
            )

            // then
            assertThat(latestResult.content.map { it.issuedCoupon.id })
                .containsExactly(issuedCoupon3.id, issuedCoupon2.id, issuedCoupon1.id)

            // when - OLDEST 정렬
            val oldestResult = couponService.findUserCoupons(
                IssuedCouponCommand.FindUserCoupons(userId = userId, sort = IssuedCouponSortType.OLDEST),
            )

            // then
            assertThat(oldestResult.content.map { it.issuedCoupon.id })
                .containsExactly(issuedCoupon1.id, issuedCoupon2.id, issuedCoupon3.id)
        }
    }

    @DisplayName("쿠폰 사용 통합테스트")
    @Nested
    inner class UseCoupon {

        @DisplayName("정액 할인 쿠폰을 사용할 수 있다")
        @Test
        fun `use fixed amount coupon successfully`() {
            // given
            val userId = 1L
            val coupon = createCoupon(
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 5000,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val orderAmount = Money.krw(10000)

            // when
            val discountAmount = couponService.useCoupon(userId, issuedCoupon.id, orderAmount)

            // then
            assertThat(discountAmount).isEqualTo(Money.krw(5000))

            val updatedIssuedCoupon = issuedCouponRepository.findById(issuedCoupon.id)
            assertThat(updatedIssuedCoupon?.status).isEqualTo(UsageStatus.USED)
            assertThat(updatedIssuedCoupon?.usedAt).isNotNull
        }

        @DisplayName("정률 할인 쿠폰을 사용할 수 있다")
        @Test
        fun `use rate coupon successfully`() {
            // given
            val userId = 1L
            val coupon = createCoupon(
                discountType = DiscountType.RATE,
                discountValue = 10,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val orderAmount = Money.krw(50000)

            // when
            val discountAmount = couponService.useCoupon(userId, issuedCoupon.id, orderAmount)

            // then
            assertThat(discountAmount).isEqualTo(Money.krw(5000))
        }

        @DisplayName("보유하지 않은 쿠폰을 사용하려고 하면 예외가 발생한다")
        @Test
        fun `throw exception when using non-existent coupon`() {
            // given
            val userId = 1L
            val nonExistentIssuedCouponId = 999L

            // when
            val exception = assertThrows<CoreException> {
                couponService.useCoupon(userId, nonExistentIssuedCouponId, Money.krw(10000))
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("보유하지 않은 쿠폰입니다")
        }

        @DisplayName("다른 사용자의 쿠폰을 사용하려고 하면 예외가 발생한다")
        @Test
        fun `throw exception when using another user's coupon`() {
            // given
            val ownerId = 1L
            val otherUserId = 2L
            val coupon = createCoupon(
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 5000,
            )
            val issuedCoupon = createIssuedCoupon(userId = ownerId, coupon = coupon)

            // when
            val exception = assertThrows<CoreException> {
                couponService.useCoupon(otherUserId, issuedCoupon.id, Money.krw(10000))
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("보유하지 않은 쿠폰입니다")
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하려고 하면 예외가 발생한다")
        @Test
        fun `throw exception when using already used coupon`() {
            // given
            val userId = 1L
            val coupon = createCoupon(
                name = "5,000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 5000,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 쿠폰을 먼저 사용
            val couponEntity = couponRepository.findById(coupon.id)!!
            issuedCoupon.use(couponEntity, Money.krw(10000))
            issuedCouponRepository.save(issuedCoupon)

            // when & then
            val exception = assertThrows<CoreException> {
                couponService.useCoupon(userId, issuedCoupon.id, Money.krw(10000))
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("이미 사용된 쿠폰입니다")
        }

        @DisplayName("할인 금액이 주문 금액보다 크면 주문 금액을 할인 금액으로 반환한다")
        @Test
        fun `discount amount is capped at order amount`() {
            // given
            val userId = 1L
            val coupon = createCoupon(
                name = "20,000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 20000,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
            val orderAmount = Money.krw(10000)

            // when
            val discountAmount = couponService.useCoupon(userId, issuedCoupon.id, orderAmount)

            // then
            assertThat(discountAmount).isEqualTo(Money.krw(10000))
        }
    }

    private fun createCoupon(
        name: String = "테스트 쿠폰",
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: Long = 1000,
    ): Coupon {
        val discountAmount = DiscountAmount(
            type = discountType,
            value = discountValue,
        )
        val coupon = Coupon.of(name = name, discountAmount = discountAmount)
        return couponRepository.save(coupon)
    }

    private fun createIssuedCoupon(
        userId: Long = 1L,
        coupon: Coupon,
    ): IssuedCoupon {
        val issuedCoupon = IssuedCoupon.issue(userId = userId, coupon = coupon)
        return issuedCouponRepository.save(issuedCoupon)
    }
}
