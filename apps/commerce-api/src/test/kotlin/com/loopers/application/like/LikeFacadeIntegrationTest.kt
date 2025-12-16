package com.loopers.application.like

import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productLikeJpaRepository: ProductLikeJpaRepository,
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

        @DisplayName("새로운 좋아요를 추가하면 ProductLike가 저장된다")
        @Test
        fun `saves ProductLike when adding new like`() {
            // given
            val userId = 1L
            val product = createProduct()

            // when
            likeFacade.addLike(userId, product.id)

            // then - LikeFacade 책임: ProductLike 저장
            val likeCount = productLikeJpaRepository.countByUserIdAndProductId(userId, product.id)
            assertThat(likeCount).isEqualTo(1)
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 추가해도 예외 없이 처리된다")
        @Test
        fun `handles duplicate like gracefully`() {
            // given
            val userId = 1L
            val product = createProduct()
            likeFacade.addLike(userId, product.id)

            // when - 중복 좋아요 (예외 없이 처리되어야 함)
            likeFacade.addLike(userId, product.id)

            // then - LikeFacade 책임: ProductLike 유지
            val likeCount = productLikeJpaRepository.countByUserIdAndProductId(userId, product.id)
            assertThat(likeCount).isEqualTo(1)
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

        @DisplayName("좋아요를 제거하면 ProductLike가 삭제된다")
        @Test
        fun `deletes ProductLike when removing like`() {
            // given
            val userId = 1L
            val product = createProduct()
            likeFacade.addLike(userId, product.id)

            // when
            likeFacade.removeLike(userId, product.id)

            // then - LikeFacade 책임: ProductLike 삭제
            val likeCount = productLikeJpaRepository.countByUserIdAndProductId(userId, product.id)
            assertThat(likeCount).isEqualTo(0)
        }

        @DisplayName("존재하지 않는 좋아요를 제거해도 예외 없이 처리된다")
        @Test
        fun `handles remove non existing like gracefully`() {
            // given
            val userId = 1L
            val product = createProduct()

            // when - 존재하지 않는 좋아요 제거 (예외 없이 처리되어야 함)
            likeFacade.removeLike(userId, product.id)

            // then - 예외 없이 정상 처리됨
            val likeCount = productLikeJpaRepository.countByUserIdAndProductId(userId, product.id)
            assertThat(likeCount).isEqualTo(0)
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
}
