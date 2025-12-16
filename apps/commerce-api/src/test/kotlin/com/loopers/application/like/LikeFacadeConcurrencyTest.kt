package com.loopers.application.like

import com.loopers.domain.like.ProductLikeRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class LikeFacadeConcurrencyTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val productLikeRepository: ProductLikeRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("여러 유저가 동일한 상품에 동시에 좋아요를 추가해도, 좋아요 개수가 정확히 증가해야 한다")
    @Test
    fun `concurrent users adding likes should increase count correctly`() {
        // given
        val product = createProduct()

        val threadCount = 10
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // when
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    val userId = index + 1L
                    likeFacade.addLike(userId, product.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(threadCount)

        // 비동기 이벤트 리스너(AFTER_COMMIT)가 likeCount를 업데이트하기 때문에 await 필요
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val productStatistic = productStatisticRepository.findByProductId(product.id)!!
            assertThat(productStatistic.likeCount).isEqualTo(threadCount.toLong())
        }
    }

    @DisplayName("여러 유저가 동일한 상품의 좋아요를 동시에 삭제해도, 좋아요 개수가 정확히 감소해야 한다")
    @Test
    fun `concurrent users removing likes should decrease count correctly`() {
        // given
        val product = createProduct()
        val threadCount = 10

        // 먼저 좋아요 추가
        repeat(threadCount) { index ->
            val userId = index + 1L
            likeFacade.addLike(userId, product.id)
        }

        // 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val initialStatistic = productStatisticRepository.findByProductId(product.id)!!
            assertThat(initialStatistic.likeCount).isEqualTo(threadCount.toLong())
        }

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // when - 동시에 좋아요 삭제
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    val userId = index + 1L
                    likeFacade.removeLike(userId, product.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(threadCount)

        // 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val finalStatistic = productStatisticRepository.findByProductId(product.id)!!
            assertThat(finalStatistic.likeCount).isEqualTo(0)
        }
    }

    @DisplayName("동일한 유저가 같은 상품에 중복으로 좋아요를 시도해도, 좋아요는 한 번만 추가되어야 한다")
    @Test
    fun `same user cannot add multiple likes to same product concurrently`() {
        // given
        val userId = 1L
        val product = createProduct()

        val threadCount = 5
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when
        repeat(threadCount) {
            executorService.submit {
                try {
                    likeFacade.addLike(userId, product.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        // 모든 요청이 성공하지만 실제로는 한 번만 추가됨 (중복 체크는 repository에서 처리)
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount)

        // 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val productStatistic = productStatisticRepository.findByProductId(product.id)!!
            assertThat(productStatistic.likeCount).isEqualTo(1)
        }
    }

    @DisplayName("여러 유저가 동시에 좋아요 추가와 삭제를 반복해도, 최종 좋아요 개수가 정확해야 한다")
    @Test
    fun `concurrent add and remove likes should maintain correct count`() {
        // given
        val product = createProduct()
        val addUserCount = 10
        val removeUserCount = 5

        // 먼저 일부 유저들의 좋아요 추가 (삭제할 대상)
        repeat(removeUserCount) { index ->
            val userId = index + 1L
            likeFacade.addLike(userId, product.id)
        }

        // 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val initialStatistic = productStatisticRepository.findByProductId(product.id)!!
            assertThat(initialStatistic.likeCount).isEqualTo(removeUserCount.toLong())
        }

        val threadCount = addUserCount + removeUserCount
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // when - 동시에 좋아요 추가와 삭제
        repeat(addUserCount) { index ->
            executorService.submit {
                try {
                    val userId = removeUserCount + index + 1L // 새로운 유저
                    likeFacade.addLike(userId, product.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        repeat(removeUserCount) { index ->
            executorService.submit {
                try {
                    val userId = index + 1L // 기존 유저
                    likeFacade.removeLike(userId, product.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then - 초기 5개에서 5개 삭제, 10개 추가 = 최종 10개
        // 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val finalStatistic = productStatisticRepository.findByProductId(product.id)!!
            assertThat(finalStatistic.likeCount).isEqualTo(addUserCount.toLong())
        }
    }

    private fun createProduct(
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(
            name = "테스트 상품",
            price = price,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }
}
