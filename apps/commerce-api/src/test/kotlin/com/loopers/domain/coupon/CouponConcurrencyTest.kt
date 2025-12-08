package com.loopers.domain.coupon

import com.loopers.IntegrationTest
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.infrastructure.coupon.CouponIssueJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.fixtures.CouponFixtures
import com.loopers.support.fixtures.UserFixtures
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class CouponConcurrencyTest : IntegrationTest() {

    private val log = LoggerFactory.getLogger(CouponConcurrencyTest::class.java)

    @Autowired
    private lateinit var couponService: CouponService

    @Autowired
    private lateinit var couponJpaRepository: CouponJpaRepository

    @Autowired
    private lateinit var couponIssueJpaRepository: CouponIssueJpaRepository

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Test
    fun `동일한 쿠폰을 여러 기기에서 동시에 사용해도 단 한 번만 사용되어야 한다`() {
        // given
        val user = createAndSaveUser()
        val coupon = createAndSaveCoupon(
            name = "10% 할인 쿠폰",
            discountType = DiscountType.RATE,
            discountValue = 10L,
        )
        val couponIssue = createAndSaveCouponIssue(user.id, coupon.id)
        val threadCount = 10

        val executor = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when
        repeat(threadCount) { index ->
            executor.submit {
                val threadName = Thread.currentThread().name
                try {
                    readyLatch.countDown()
                    startLatch.await()

                    log.info("[$threadName] 시작")
                    couponService.applyCoupon(user.id, coupon.id)
                    successCount.incrementAndGet()
                    log.info("[$threadName] 성공")
                } catch (e: ObjectOptimisticLockingFailureException) {
                    failCount.incrementAndGet()
                    log.info("[$threadName] 낙관적 락 충돌로 실패: ${e.message}")
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    log.error("[$threadName] 예상치 못한 예외: ${e.message}")
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await()

        executor.shutdown()

        // then: 정확히 1번만 성공
        val updatedCouponIssue = couponIssueJpaRepository.findById(couponIssue.id!!).orElseThrow()

        assertSoftly { soft ->
            soft.assertThat(successCount.get()).isEqualTo(1)
            soft.assertThat(failCount.get()).isEqualTo(threadCount - 1)
            soft.assertThat(updatedCouponIssue.status).isEqualTo(CouponStatus.USED)
            soft.assertThat(updatedCouponIssue.usedAt).isNotNull()
            soft.assertThat(updatedCouponIssue.version).isEqualTo(1L)
        }
    }

    private fun createAndSaveUser(
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

    private fun createAndSaveCoupon(
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

    private fun createAndSaveCouponIssue(userId: Long, couponId: Long): CouponIssue {
        val couponIssue = CouponFixtures.createCouponIssue(
            userId = userId,
            couponId = couponId,
        )
        return couponIssueJpaRepository.save(couponIssue)
    }
}
