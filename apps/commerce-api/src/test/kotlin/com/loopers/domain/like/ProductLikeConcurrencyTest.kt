package com.loopers.domain.like

import com.loopers.IntegrationTest
import com.loopers.domain.product.Product
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.fixtures.ProductFixtures
import com.loopers.support.fixtures.UserFixtures
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ProductLikeConcurrencyTest : IntegrationTest() {

    private val log = LoggerFactory.getLogger(ProductLikeConcurrencyTest::class.java)

    @Autowired
    private lateinit var productLikeService: ProductLikeService

    @Autowired
    private lateinit var productLikeRepository: ProductLikeJpaRepository

    @Autowired
    private lateinit var productJpaRepository: ProductJpaRepository

    @Autowired
    private lateinit var userRepository: UserJpaRepository

    @Nested
    @DisplayName("좋아요 동시성")
    inner class LikeConcurrency {

        @Test
        fun `한명의 유저가_같은상품을_동시에_100번_눌러도_좋아요는_1개만_생성된다`() {
            // given
            val product = createAndSaveProduct(
                name = "테스트상품",
                price = 1000L,
                brandId = 1L,
            )

            val user = userRepository.save(
                UserFixtures.createUser(userId = "user1"),
            )

            val threadCount = 100
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)

            val success = AtomicInteger(0)
            val fail = AtomicInteger(0)

            // when
            repeat(threadCount) {
                executor.submit {
                    try {
                        productLikeService.like(product, user)
                        success.incrementAndGet()
                    } catch (e: Exception) {
                        fail.incrementAndGet()
                        log.warn("예외 발생: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then
            val likes = productLikeRepository.findAllByProductId(product.id)

            assertSoftly { soft ->
                soft.assertThat(likes.size).isEqualTo(1)
                soft.assertThat(success.get() + fail.get()).isEqualTo(threadCount)
            }
        }

        @Test
        fun `여러명이 동시에_좋아요를_눌러도_사용자 수 만큼_생성된다`() {
            // given
            val product = createAndSaveProduct(
                name = "테스트상품",
                price = 1000L,
                brandId = 1L,
            )

            val users = (1..100).map {
                userRepository.save(UserFixtures.createUser(userId = "user$it"))
            }

            val threadCount = 100
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)

            val success = AtomicInteger(0)
            val fail = AtomicInteger(0)

            // when
            repeat(threadCount) { index ->
                executor.submit {
                    try {
                        val user = users[index]
                        productLikeService.like(product, user)
                        success.incrementAndGet()
                    } catch (e: Exception) {
                        fail.incrementAndGet()
                        log.warn("예외 발생: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then
            val likes = productLikeRepository.findAllByProductId(product.id)

            assertSoftly { soft ->
                soft.assertThat(likes.size).isEqualTo(100)
                soft.assertThat(success.get()).isEqualTo(100)
                soft.assertThat(fail.get()).isEqualTo(0)
            }
        }
    }

    @Nested
    @DisplayName("좋아요 취소 동시성")
    inner class UnlikeConcurrency {

        @Test
        fun `50명중 30명이 동시에 좋아요_취소할때_최종 좋아요는 20개가 된다`() {
            // given
            val product = createAndSaveProduct(
                name = "테스트상품",
                price = 1000L,
                brandId = 1L,
            )

            // 유저 50명 생성 + 모두 좋아요
            val users = (1..50).map { idx ->
                val user = userRepository.save(
                    UserFixtures.createUser(userId = "user$idx"),
                )
                productLikeService.like(product, user)
                user
            }

            // 취소 요청할 유저 30명 선택
            val usersToUnlike = users.take(30)

            val threadCount = usersToUnlike.size
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)

            val success = AtomicInteger(0)
            val fail = AtomicInteger(0)

            // when
            usersToUnlike.forEach { user ->
                executor.submit {
                    try {
                        productLikeService.unlike(product, user)
                        success.incrementAndGet()
                    } catch (e: Exception) {
                        fail.incrementAndGet()
                        log.warn("예외 발생: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then
            val remainingLikes = productLikeRepository.findAllByProductId(product.id)

            assertSoftly { soft ->
                soft.assertThat(remainingLikes.size)
                    .isEqualTo(20)

                soft.assertThat(success.get())
                    .isEqualTo(30)

                soft.assertThat(fail.get())
                    .isEqualTo(0)
            }
        }
    }

    private fun createAndSaveProduct(name: String, price: Long, brandId: Long): Product {
        return productJpaRepository.save(
            ProductFixtures.createProduct(
                name = name,
                price = price,
                brandId = brandId,
            ),
        )
    }
}
