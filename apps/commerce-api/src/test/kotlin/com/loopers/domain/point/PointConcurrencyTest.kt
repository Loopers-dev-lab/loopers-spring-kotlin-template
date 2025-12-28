package com.loopers.domain.point

import com.loopers.domain.order.Money
import com.loopers.domain.product.Currency
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
@DisplayName("Point 동시성 테스트")
class PointConcurrencyTest {

    @Autowired
    private lateinit var pointService: PointService

    @Autowired
    private lateinit var testFixtures: TestFixtures

    private var userId by Delegates.notNull<Long>()

    @BeforeEach
    fun setUp() {
        testFixtures.clear()

        val user = testFixtures.saveUser()
        userId = user.id

        // 사용자에게 100,000원 포인트 충전
        val point = Point(
            userId = userId,
            balance = Money(BigDecimal("100000"), Currency.KRW),
        )
        testFixtures.savePoint(point)
    }

    @Test
    @DisplayName("동일한 유저가 서로 다른 주문을 동시에 수행해도 포인트가 정상적으로 차감되어야 한다")
    fun concurrency_sameUserMultipleOrders_shouldDeductPointsCorrectly() {
        // given
        val numberOfOrders = 10
        val deductAmount = Money(BigDecimal("5000"), Currency.KRW)
        val latch = CountDownLatch(numberOfOrders)
        val executor = Executors.newFixedThreadPool(numberOfOrders)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 10개의 주문이 동시에 5000원씩 차감 시도
        repeat(numberOfOrders) {
            executor.submit {
                try {
                    pointService.deductPoint(userId, deductAmount)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("포인트 차감 실패: ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 모두 성공해야 함 (100,000원에서 50,000원 차감)
        assertThat(successCount.get()).isEqualTo(10)
        assertThat(failureCount.get()).isEqualTo(0)

        // 최종 포인트 잔액 확인
        val point = pointService.getPoint(userId)
        val expectedBalance = BigDecimal("100000") - (BigDecimal("5000") * BigDecimal("10"))
        assertThat(point.balance.amount).isEqualByComparingTo(expectedBalance)
    }

    @Test
    @DisplayName("포인트가 부족한 상황에서 동시에 여러 주문이 들어오면 일부만 성공해야 한다")
    fun concurrency_insufficientPoints_shouldPartiallySucceed() {
        // given
        val numberOfOrders = 10
        val deductAmount = Money(BigDecimal("15000"), Currency.KRW)
        val latch = CountDownLatch(numberOfOrders)
        val executor = Executors.newFixedThreadPool(numberOfOrders)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 10개의 주문이 동시에 15,000원씩 차감 시도 (총 150,000원 필요하지만 100,000원만 있음)
        repeat(numberOfOrders) {
            executor.submit {
                try {
                    pointService.deductPoint(userId, deductAmount)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 100,000 / 15,000 = 6번 성공, 4번 실패
        assertThat(successCount.get()).isEqualTo(6)
        assertThat(failureCount.get()).isEqualTo(4)

        // 최종 포인트 잔액 확인 (15,000 * 6 = 90,000 차감)
        val point = pointService.getPoint(userId)
        val expectedBalance = BigDecimal("100000") - (BigDecimal("15000") * BigDecimal("6"))
        assertThat(point.balance.amount).isEqualByComparingTo(expectedBalance)
    }

    @Test
    @DisplayName("포인트 충전과 차감이 동시에 일어나도 정합성이 보장되어야 한다")
    fun concurrency_chargeAndDeductSimultaneously_shouldMaintainConsistency() {
        // given
        val numberOfOperations = 20
        val amount = Money(BigDecimal("5000"), Currency.KRW)
        val latch = CountDownLatch(numberOfOperations)
        val executor = Executors.newFixedThreadPool(numberOfOperations)
        val chargeCount = AtomicInteger(0)
        val deductCount = AtomicInteger(0)

        // when: 10번 충전, 10번 차감이 동시에 발생
        repeat(numberOfOperations) { index ->
            executor.submit {
                try {
                    if (index % 2 == 0) {
                        pointService.chargePoint(userId, amount)
                        chargeCount.incrementAndGet()
                    } else {
                        pointService.deductPoint(userId, amount)
                        deductCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    println("포인트 작업 실패 (index=$index): ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 최종 잔액 = 초기 100,000 + (충전 횟수 - 차감 횟수) * 5,000
        val point = pointService.getPoint(userId)
        val expectedBalance = BigDecimal("100000") +
                (BigDecimal("5000") * BigDecimal(chargeCount.get().toString())) -
                (BigDecimal("5000") * BigDecimal(deductCount.get().toString()))
        assertThat(point.balance.amount).isEqualByComparingTo(expectedBalance)
    }
}
