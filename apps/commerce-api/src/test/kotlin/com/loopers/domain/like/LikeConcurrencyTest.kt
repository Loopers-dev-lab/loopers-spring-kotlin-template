package com.loopers.domain.like

import com.loopers.fixtures.createTestBrand
import com.loopers.fixtures.createTestProduct
import com.loopers.fixtures.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Like 동시성 테스트")
class LikeConcurrencyTest {

    @Autowired
    private lateinit var likeService: LikeService

    @Autowired
    private lateinit var likeQueryService: LikeQueryService

    @Autowired
    private lateinit var testFixtures: TestFixtures

    private var productId by Delegates.notNull<Long>()
    private val userIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        testFixtures.clear()

        val brand = testFixtures.saveBrand(createTestBrand())
        val product = testFixtures.saveProduct(createTestProduct(brand = brand))
        productId = product.id

        // 10명의 사용자 생성
        repeat(10) {
            val user = testFixtures.saveUser()
            userIds.add(user.id)
        }
    }

    @Test
    @DisplayName("동일한 상품에 대해 여러 사용자가 동시에 좋아요를 요청해도 정상 반영되어야 한다")
    fun concurrency_multipleLikesOnSameProduct_shouldBeProperlyHandled() {
        // given
        val numberOfThreads = 10
        val latch = CountDownLatch(numberOfThreads)
        val executor = Executors.newFixedThreadPool(numberOfThreads)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 10명의 사용자가 동시에 같은 상품에 좋아요
        userIds.forEachIndexed { index, userId ->
            executor.submit {
                try {
                    likeService.addLike(userId, productId)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("좋아요 실패 (userId=$userId): ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 10건 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(10)
        assertThat(failureCount.get()).isEqualTo(0)

        // 실제 좋아요 수 확인
        val likeCount = likeQueryService.countByProductId(productId)
        assertThat(likeCount).isEqualTo(10)
    }

    @Test
    @DisplayName("동일한 사용자가 동일한 상품에 대해 좋아요/취소를 반복해도 정상 처리되어야 한다")
    fun concurrency_sameUserRepeatedLikeAndUnlike_shouldBeProperlyHandled() {
        // given
        val userId = userIds.first()
        val numberOfThreads = 10
        val latch = CountDownLatch(numberOfThreads)
        val executor = Executors.newFixedThreadPool(numberOfThreads)
        val successCount = AtomicInteger(0)

        // when: 같은 사용자가 동시에 좋아요 요청 (멱등성 테스트)
        repeat(numberOfThreads) {
            executor.submit {
                try {
                    likeService.addLike(userId, productId)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("좋아요 실패: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 멱등성 보장으로 모두 성공
        assertThat(successCount.get()).isEqualTo(numberOfThreads)

        // 실제로는 한 번만 좋아요가 등록되어야 함
        val likeCount = likeQueryService.countByProductId(productId)
        assertThat(likeCount).isEqualTo(1)
    }
}
