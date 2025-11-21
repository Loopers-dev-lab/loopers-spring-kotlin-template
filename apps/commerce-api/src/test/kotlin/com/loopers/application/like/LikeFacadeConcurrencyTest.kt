package com.loopers.application.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
import com.loopers.domain.product.Product
import com.loopers.domain.user.User
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class LikeFacadeConcurrencyTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val likeJpaRepository: LikeJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    ) {
    @AfterEach
    fun cleanUp() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 상품에 대해 여러명이 좋아요를 요청해도, 상품의 좋아요 개수가 정상 반영되어야 한다.")
    @Test
    fun multipleLikeOnSameProduct() {
        // arrange
        val userCount = 10

        // act
        val userList = ArrayList<User>()
        for (i: Int in 1..userCount) {
            val user = userJpaRepository.save(
                User(
                    username = "testUser$i",
                    password = "testPassword",
                    email = "test@test.com",
                    birthDate = "2025-10-25",
                    gender = User.Gender.MALE,
                ),
            )
            userList.add(user)
        }

        val brand = brandJpaRepository.save(Brand(name = "testBrand"))
        val product = productJpaRepository.save(
            Product(
                name = "testProduct",
                price = BigDecimal("1.00"),
                brandId = brand.id,
            ),
        )

        val latch = CountDownLatch(userCount)
        val executor = Executors.newFixedThreadPool(userCount)

        for (user in userList) {
            executor.submit {
                try {
                    likeFacade.addLike(userId = user.id, productId = product.id)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // assert
        val result = likeJpaRepository.countByProductId(product.id)
        Assertions.assertThat(result).isEqualTo(userCount.toLong())
    }

    @DisplayName("동일한 상품에 대해 여러명이 좋아요 취소를 요청해도, 상품의 좋아요 개수가 정상 반영되어야 한다.")
    @Test
    fun multipleRemoveLikeOnSameProduct() {
        // arrange
        val userCount = 10

        // act
        val userList = ArrayList<User>()
        for (i: Int in 1..userCount) {
            val user = userJpaRepository.save(
                User(
                    username = "testUser$i",
                    password = "testPassword",
                    email = "test@test.com",
                    birthDate = "2025-10-25",
                    gender = User.Gender.MALE,
                ),
            )
            userList.add(user)
        }

        val brand = brandJpaRepository.save(Brand(name = "testBrand"))
        val product = productJpaRepository.save(
            Product(
                name = "testProduct",
                price = BigDecimal("1.00"),
                brandId = brand.id,
            ),
        )

        for (user in userList) {
            likeJpaRepository.save(Like(userId = user.id, productId = product.id))
        }

        val latch = CountDownLatch(userCount)
        val executor = Executors.newFixedThreadPool(userCount)

        for (user in userList) {
            executor.submit {
                try {
                    likeFacade.removeLike(userId = user.id, productId = product.id)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // assert
        val result = likeJpaRepository.countByProductId(product.id)
        Assertions.assertThat(result).isEqualTo(0)
    }
}
