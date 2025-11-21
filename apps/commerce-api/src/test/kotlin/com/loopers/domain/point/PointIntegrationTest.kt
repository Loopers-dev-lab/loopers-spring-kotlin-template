package com.loopers.domain.point

import com.loopers.domain.user.User
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
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
class PointIntegrationTest @Autowired constructor(
    private val pointService: PointService,
    private val pointJpaRepository: PointJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private lateinit var user: User
    private lateinit var point: Point

    @BeforeEach
    fun setUp() {
        user = userJpaRepository.save(
            User(
                username = "testuser",
                password = "password123",
                email = "test@example.com",
                birthDate = "1997-03-25",
                gender = User.Gender.MALE,
            ),
        )

        point = pointJpaRepository.save(
            Point.of(
                userId = user.id,
                initialBalance = BigDecimal("100000.00"),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 유저가 서로 다른 주문을 동시에 수행해도, 포인트가 정상적으로 차감되어야 한다")
    @Test
    fun whenSameUserDeductsPointConcurrently_thenPointShouldBeDeductedCorrectly() {
        // arrange
        val threadCount = 10
        val deductAmount = BigDecimal("1000.00")
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // act
        repeat(threadCount) {
            executorService.submit {
                try {
                    pointService.deductPoint(user.id, deductAmount)
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
        val updatedPoint = pointJpaRepository.findById(point.id).orElseThrow()
        val expectedBalance = BigDecimal("100000.00").subtract(deductAmount.multiply(BigDecimal(successCount.get())))

        assertThat(updatedPoint.balance).isEqualByComparingTo(expectedBalance)
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount)
    }
}
