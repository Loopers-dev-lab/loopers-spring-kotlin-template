package com.loopers.application.like

import com.loopers.IntegrationTestSupport
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.domain.user.UserFixture
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.signal.ProductTotalSignalJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class LikeFacadeTest(
    databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val productTotalSignalRepository: ProductTotalSignalJpaRepository,
    private val likeFacade: LikeFacade,
) : IntegrationTestSupport() {

    @DisplayName("좋아요 테스트")
    @Nested
    inner class Like {
        @DisplayName("동일한 상품에 대하여, 사용자가 동시에 15개의 좋아요를 누른 경우, 정상적으로 호출된다.")
        @Test
        fun likeSuccess_whenTenUsersLikeSameProductAtSameTime() {
            // arrange
            val numberOfThreads = 15
            val latch = CountDownLatch(numberOfThreads)
            val executor = Executors.newFixedThreadPool(numberOfThreads)
            val loginId = "testUser"

            for (i in 1..numberOfThreads) {
                val testUser = UserFixture.create(loginId = loginId + i)
                userRepository.save(testUser)
            }
            val testProduct = ProductModel.create(
                name = "testProduct",
                stock = 100,
                price = Money(BigDecimal.valueOf(5000L)),
                refBrandId = 12,
            )
            productRepository.save(testProduct)
            val productId = testProduct.id

            val signal = ProductTotalSignalModel(refProductId = productId)
            productTotalSignalRepository.save(signal)

            // act
            repeat(numberOfThreads) { index ->
                executor.submit {
                    try {
                        val userId = (index + 1).toLong()
                        likeFacade.like(userId, productId)
                    } catch (e: Exception) {
                        println("실패: \${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()

            // assert
            val productSignal = productTotalSignalRepository.findByRefProductId(productId)
            assertThat(productSignal?.likeCount).isEqualTo(
                numberOfThreads.toLong(),
            )
        }
    }
}
