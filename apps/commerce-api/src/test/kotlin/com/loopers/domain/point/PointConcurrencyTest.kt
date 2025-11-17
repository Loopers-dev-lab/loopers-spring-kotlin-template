package com.loopers.domain.point

import com.loopers.IntegrationTest
import com.loopers.domain.user.User
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.fixtures.UserFixtures
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class PointConcurrencyTest : IntegrationTest() {

    private val log = LoggerFactory.getLogger(PointConcurrencyTest::class.java)

    @Autowired
    private lateinit var pointService: PointService

    @Autowired
    private lateinit var pointJpaRepository: PointJpaRepository

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Nested
    @DisplayName("포인트 충전 동시성 테스트")
    inner class ChargeTest {

        @Test
        fun `동일 사용자가 동시에 포인트를 충전해도 모든 충전이 정확히 반영되어야 한다`() {
            // given
            val user = createAndSaveUser()
            pointService.init(user.id)

            val threadCount = 100
            val chargeAmount = 1000L
            val expectedTotalAmount = threadCount * chargeAmount  // 100 * 1000 = 100,000

            // when: 100개 스레드가 동시에 1000원씩 충전
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            repeat(threadCount) {
                executor.submit {
                    try {
                        pointService.charge(chargeAmount, user.id)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                        log.warn("충전 실패: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then: Lost Update 없이 정확히 100,000원이 충전되어야 함
            val finalPoint = pointJpaRepository.findByIdOrNull(user.id)!!.amount.value
            assertSoftly { soft ->
                soft.assertThat(finalPoint).isEqualTo(expectedTotalAmount)
                soft.assertThat(successCount.get()).isEqualTo(threadCount)
                soft.assertThat(failCount.get()).isEqualTo(0)
            }
        }
    }

    @Nested
    @DisplayName("포인트 사용 동시성 테스트")
    inner class UseTest {

        @Test
        @DisplayName("동일 사용자가 동시에 포인트를 사용해도 모든 사용이 정확히 반영되어야 한다")
        fun usePoint_concurrent_sameUser() {
            // given: 초기 잔액 100,000원
            val user = createAndSaveUser()
            val initialAmount = 100000L
            pointService.init(user.id)
            pointService.charge(initialAmount, user.id)

            val threadCount = 100
            val useAmount = 100L
            val expectedRemainingAmount = initialAmount - (threadCount * useAmount)  // 100,000 - 10,000 = 90,000

            // when: 100개 스레드가 동시에 100원씩 사용
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            repeat(threadCount) {
                executor.submit {
                    try {
                        pointService.use(useAmount, user.id)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                        log.warn("사용 실패: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then: Lost Update 없이 정확히 90,000원이 남아야 함
            val finalPoint = pointJpaRepository.findByIdOrNull(user.id)!!.amount.value
            assertSoftly { soft ->
                soft.assertThat(finalPoint).isEqualTo(expectedRemainingAmount)
                soft.assertThat(successCount.get()).isEqualTo(threadCount)
                soft.assertThat(failCount.get()).isEqualTo(0)
            }
        }

        @Test
        fun `동일 사용자가 보유 포인트보다 많은 금액을 동시에 사용하려고 하면 정확히 잔액만큼만 사용되어야 한다`() {
            // given: 초기 잔액 5,000원
            val user = createAndSaveUser()
            val initialAmount = 5000L
            pointService.init(user.id)
            pointService.charge(initialAmount, user.id)

            val threadCount = 10
            val useAmount = 1000L  // 10개 요청 * 1000원 = 10,000원 시도 (잔액 초과)

            // when: 10개 스레드가 동시에 1000원씩 사용 시도
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            repeat(threadCount) {
                executor.submit {
                    try {
                        pointService.use(useAmount, user.id)
                        successCount.incrementAndGet()
                    } catch (e: CoreException) {
                        failCount.incrementAndGet()  // 잔액 부족 예외
                    } catch (e: Exception) {
                        log.warn("예상치 못한 예외: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then: 5번만 성공하고, 나머지는 실패해야 함 (음수 방지)
            val finalPoint = pointJpaRepository.findByIdOrNull(user.id)!!.amount.value

            val expectedSuccessCount = (initialAmount / useAmount).toInt()  // 5,000 / 1,000 = 5
            val expectedRemainingAmount = initialAmount - (expectedSuccessCount * useAmount)  // 5,000 - 5,000 = 0

            assertSoftly { soft ->
                soft.assertThat(successCount.get()).isEqualTo(expectedSuccessCount)
                soft.assertThat(finalPoint).isEqualTo(expectedRemainingAmount)
                soft.assertThat(finalPoint).isGreaterThanOrEqualTo(0)  // 음수 방지 검증
            }
        }
    }

    @Nested
    @DisplayName("포인트 충전과 사용 동시 실행 테스트")
    inner class ChargeAndUseTest {

        @Test
        fun `동일 사용자가 동시에 포인트를 충전하고 사용해도 최종 잔액이 정확해야 한다`() {
            // given: 초기 잔액 10,000원
            val user = createAndSaveUser()
            val initialAmount = 10000L
            pointService.init(user.id)
            pointService.charge(initialAmount, user.id)

            val numberOfCharges = 50
            val numberOfUses = 30
            val chargeAmount = 2000L  // 50번 * 2,000 = +100,000원
            val useAmount = 1000L     // 30번 * 1,000 = -30,000원

            // 예상: 10,000 + 100,000 - 30,000 = 80,000원
            val expectedFinalAmount = initialAmount +
                    (numberOfCharges * chargeAmount) -
                    (numberOfUses * useAmount)

            val totalThreads = numberOfCharges + numberOfUses

            // when: 충전과 사용이 무작위 순서로 동시 실행
            val executor = Executors.newFixedThreadPool(totalThreads)
            val latch = CountDownLatch(totalThreads)
            val chargeSuccessCount = AtomicInteger(0)
            val useSuccessCount = AtomicInteger(0)

            // 충전 스레드 50개
            repeat(numberOfCharges) {
                executor.submit {
                    try {
                        pointService.charge(chargeAmount, user.id)
                        chargeSuccessCount.incrementAndGet()
                    } catch (e: Exception) {
                        log.warn("충전 실패: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // 사용 스레드 30개
            repeat(numberOfUses) {
                executor.submit {
                    try {
                        pointService.use(useAmount, user.id)
                        useSuccessCount.incrementAndGet()
                    } catch (e: Exception) {
                        log.warn("사용 실패: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then: 충전/사용 순서와 무관하게 최종 잔액은 80,000원이어야 함
            val finalPoint = pointJpaRepository.findByIdOrNull(user.id)!!.amount.value

            assertSoftly { soft ->
                soft.assertThat(finalPoint).isEqualTo(expectedFinalAmount)
                soft.assertThat(chargeSuccessCount.get()).isEqualTo(numberOfCharges)
                soft.assertThat(useSuccessCount.get()).isEqualTo(numberOfUses)
            }
        }
    }

    private fun createAndSaveUser(userId: String = "userId"): User {
        return userJpaRepository.save(UserFixtures.createUser(userId = userId))
    }
}
