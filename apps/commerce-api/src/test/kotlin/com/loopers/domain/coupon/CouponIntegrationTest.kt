package com.loopers.domain.coupon

import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class CouponIntegrationTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private lateinit var coupon: Coupon
    private lateinit var userCoupon: UserCoupon

    @BeforeEach
    fun setUp() {
        coupon = couponJpaRepository.save(
            Coupon.of(
                name = "10% 할인 쿠폰",
                type = CouponType.PERCENTAGE,
                discountValue = BigDecimal("10.00"),
            ),
        )

        userCoupon = userCouponJpaRepository.save(
            UserCoupon.of(
                userId = 1L,
                couponId = coupon.id,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한번만 사용되어야 한다")
    @Test
    fun whenSameCouponUsedConcurrently_thenOnlyOneSucceeds() {
        // arrange
        val threadCount = 10
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // act
        repeat(threadCount) {
            executorService.submit {
                try {
                    couponService.useCouponAndCalculateFinalAmount(userCoupon.id, BigDecimal("10000.00"))
                    successCount.incrementAndGet()
                } catch (e: CoreException) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // assert
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(threadCount - 1)

        val updatedUserCoupon = userCouponJpaRepository.findById(userCoupon.id).orElseThrow()
        assertThat(updatedUserCoupon.used).isTrue()
    }
}
