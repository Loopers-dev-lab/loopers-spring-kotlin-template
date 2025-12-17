package com.loopers.domain.coupon

import com.loopers.fixtures.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Coupon 동시성 테스트")
class CouponConcurrencyTest {

    @Autowired
    private lateinit var couponService: CouponService

    @Autowired
    private lateinit var userCouponRepository: UserCouponRepository

    @Autowired
    private lateinit var testFixtures: TestFixtures

    private var userId by Delegates.notNull<Long>()
    private lateinit var coupon: Coupon
    private lateinit var userCoupon: UserCoupon

    @BeforeEach
    fun setUp() {
        testFixtures.clear()

        val user = testFixtures.saveUser()
        userId = user.id

        // 5000원 할인 쿠폰 생성
        coupon = testFixtures.saveCoupon(
            Coupon(
                name = "5000원 할인 쿠폰",
                discountType = CouponType.FIXED_AMOUNT,
                discountValue = BigDecimal("5000"),
            ),
        )

        // 사용자에게 쿠폰 발급
        userCoupon = testFixtures.saveUserCoupon(
            UserCoupon(
                userId = userId,
                coupon = coupon,
            ),
        )
    }

    @Test
    @DisplayName("동일한 쿠폰으로 여러 스레드에서 동시에 주문해도 쿠폰은 단 한 번만 사용되어야 한다")
    fun concurrency_sameCouponUsedMultipleTimes_shouldBeUsedOnlyOnce() {
        // given
        val numberOfThreads = 10
        val latch = CountDownLatch(numberOfThreads)
        val executor = Executors.newFixedThreadPool(numberOfThreads)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 같은 쿠폰을 동시에 10번 사용 시도
        repeat(numberOfThreads) {
            executor.submit {
                try {
                    couponService.useUserCoupon(userId, userCoupon.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("쿠폰 사용 실패: ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 단 한 번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(9)

        // DB 확인: 쿠폰이 사용됨 상태여야 함
        val savedUserCoupon = userCouponRepository.findById(userCoupon.id)
        assertThat(savedUserCoupon).isNotNull
        savedUserCoupon?.let {
            assertThat(it.isUsed).isTrue()
            assertThat(it.usedAt).isNotNull()
        }
    }

    @Test
    @DisplayName("여러 사용자가 각자의 쿠폰을 동시에 사용해도 모두 정상 처리되어야 한다")
    fun concurrency_multipleUsersUseTheirOwnCoupons_shouldAllSucceed() {
        // given
        val numberOfUsers = 5
        val latch = CountDownLatch(numberOfUsers)
        val executor = Executors.newFixedThreadPool(numberOfUsers)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val userCoupons = mutableListOf<Pair<Long, UserCoupon>>()

        // 5명의 사용자에게 각각 쿠폰 발급
        repeat(numberOfUsers) {
            val user = testFixtures.saveUser()
            val uc = testFixtures.saveUserCoupon(
                UserCoupon(
                    userId = user.id,
                    coupon = coupon,
                ),
            )
            userCoupons.add(Pair(user.id, uc))
        }

        // when: 5명이 동시에 각자의 쿠폰 사용
        userCoupons.forEach { (uid, uc) ->
            executor.submit {
                try {
                    couponService.useUserCoupon(uid, uc.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("쿠폰 사용 실패 (userId=$uid): ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(numberOfUsers)
        assertThat(failureCount.get()).isEqualTo(0)

        // 모든 쿠폰이 사용됨 상태여야 함
        userCoupons.forEach { (_, uc) ->
            val savedUserCoupon = userCouponRepository.findById(uc.id)
            savedUserCoupon?.let {
                assertThat(it.isUsed).isTrue()
            }
        }
    }
}
