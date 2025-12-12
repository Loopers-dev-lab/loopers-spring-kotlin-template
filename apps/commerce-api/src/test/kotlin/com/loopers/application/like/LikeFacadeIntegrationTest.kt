package com.loopers.application.like

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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.TimeUnit

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productStatisticRepository: ProductStatisticRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 추가 통합테스트")
    @Nested
    inner class AddLike {

        @DisplayName("새로운 좋아요를 추가하면 ProductLike가 저장되고 likeCount가 증가한다")
        @Test
        fun `save like and increase like count when add new like`() {
            // given
            val userId = 1L
            val product = createProduct()
            val initialLikeCount = getProductLikeCount(product.id)

            // when
            likeFacade.addLike(userId, product.id)

            // then - 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val updatedLikeCount = getProductLikeCount(product.id)
                assertThat(updatedLikeCount).isEqualTo(initialLikeCount + 1)
            }
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 추가하면 likeCount가 증가하지 않는다")
        @Test
        fun `not increase like count when add duplicate like`() {
            // given
            val userId = 1L
            val product = createProduct()
            likeFacade.addLike(userId, product.id)

            // 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val likeCount = getProductLikeCount(product.id)
                assertThat(likeCount).isEqualTo(1)
            }
            val likeCountAfterFirstAdd = getProductLikeCount(product.id)

            // when
            likeFacade.addLike(userId, product.id)

            // then - 약간의 지연 후 확인 (중복이므로 이벤트가 발행되지 않음)
            Thread.sleep(500)
            val likeCountAfterSecondAdd = getProductLikeCount(product.id)
            assertThat(likeCountAfterSecondAdd).isEqualTo(likeCountAfterFirstAdd)
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 추가하면 예외가 발생한다")
        @Test
        fun `throw exception when add like to non existing product`() {
            // given
            val userId = 1L
            val nonExistingProductId = 999L

            // when & then
            val exception = org.junit.jupiter.api.assertThrows<com.loopers.support.error.CoreException> {
                likeFacade.addLike(userId, nonExistingProductId)
            }

            assertThat(exception.errorType).isEqualTo(com.loopers.support.error.ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("상품을 찾을 수 없습니다")
        }
    }

    @DisplayName("좋아요 제거 통합테스트")
    @Nested
    inner class RemoveLike {

        @DisplayName("좋아요를 제거하면 ProductLike가 삭제되고 likeCount가 감소한다")
        @Test
        fun `delete like and decrease like count when remove like`() {
            // given
            val userId = 1L
            val product = createProduct()
            likeFacade.addLike(userId, product.id)

            // 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val likeCount = getProductLikeCount(product.id)
                assertThat(likeCount).isEqualTo(1)
            }
            val likeCountAfterAdd = getProductLikeCount(product.id)

            // when
            likeFacade.removeLike(userId, product.id)

            // then - 비동기 이벤트 리스너가 likeCount를 업데이트하기 때문에 await 필요
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val likeCountAfterRemove = getProductLikeCount(product.id)
                assertThat(likeCountAfterRemove).isEqualTo(likeCountAfterAdd - 1)
            }
        }

        @DisplayName("존재하지 않는 좋아요를 제거하면 likeCount가 변하지 않는다")
        @Test
        fun `not change like count when remove non existing like`() {
            // given
            val userId = 1L
            val product = createProduct()
            val initialLikeCount = getProductLikeCount(product.id)

            // when
            likeFacade.removeLike(userId, product.id)

            // then
            val likeCountAfterRemove = getProductLikeCount(product.id)
            assertThat(likeCountAfterRemove).isEqualTo(initialLikeCount)
        }

        @DisplayName("존재하지 않는 상품의 좋아요를 제거하면 예외가 발생한다")
        @Test
        fun `throw exception when remove like from non existing product`() {
            // given
            val userId = 1L
            val nonExistingProductId = 999L

            // when & then
            val exception = org.junit.jupiter.api.assertThrows<com.loopers.support.error.CoreException> {
                likeFacade.removeLike(userId, nonExistingProductId)
            }

            assertThat(exception.errorType).isEqualTo(com.loopers.support.error.ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("상품을 찾을 수 없습니다")
        }
    }

    private fun createProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(
            name = name,
            price = price,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun getProductLikeCount(productId: Long): Long {
        return productStatisticRepository.findByProductId(productId)?.likeCount ?: 0L
    }
}
