package com.loopers.domain.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.user.User
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
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

@SpringBootTest
class LikeIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val likeJpaRepository: LikeJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private lateinit var product: Product
    private val userIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        val brand = brandJpaRepository.save(
            Brand.of(name = "Test Brand"),
        )

        product = productJpaRepository.save(
            Product.of(
                name = "Test Product",
                price = BigDecimal("10000.00"),
                brandId = brand.id,
            ),
        )

        repeat(100) { i ->
            val user = userJpaRepository.save(
                User(
                    username = "user$i",
                    password = "password123",
                    email = "user$i@test.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                ),
            )
            userIds.add(user.id)
        }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        userIds.clear()
    }

    @DisplayName("동일한 상품에 대해 여러명이 좋아요를 요청해도, 좋아요 개수가 정상 반영되어야 한다")
    @Test
    fun whenMultipleUsersAddLikeConcurrently_thenLikeCountShouldBeAccurate() {
        // arrange
        val threadCount = 100
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // act
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    likeService.addLike(userIds[index], product.id)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // assert
        val likeCount = likeJpaRepository.countByProductId(product.id)
        assertThat(likeCount).isEqualTo(threadCount.toLong())
    }
}
