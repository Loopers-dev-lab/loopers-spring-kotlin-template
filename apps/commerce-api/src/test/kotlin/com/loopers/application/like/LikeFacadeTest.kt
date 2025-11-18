package com.loopers.application.like

import com.loopers.domain.like.ProductLikeRepository
import com.loopers.domain.like.ProductLikeRepository.DeleteResult.Deleted
import com.loopers.domain.like.ProductLikeRepository.DeleteResult.NotExist
import com.loopers.domain.like.ProductLikeRepository.SaveResult.AlreadyExists
import com.loopers.domain.like.ProductLikeRepository.SaveResult.Created
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatisticRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LikeFacadeTest {
    private lateinit var productLikeRepository: ProductLikeRepository
    private lateinit var productStatisticRepository: ProductStatisticRepository

    private lateinit var likeService: ProductLikeService
    private lateinit var productService: ProductService

    private lateinit var likeFacade: LikeFacade

    @BeforeEach
    fun setUp() {
        productLikeRepository = mockk()
        productStatisticRepository = mockk()

        likeService = ProductLikeService(productLikeRepository)
        productService = mockk()

        // Facade 생성
        likeFacade = LikeFacade(likeService, productService)
    }

    @Nested
    @DisplayName("멱등성 테스트")
    inner class Idempotent {
        @Test
        @DisplayName("중복 좋아요 추가 시 count가 증가하지 않는다")
        fun `does not increase count when duplicate like is added`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productService.findProductById(productId) } returns mockk()
            every { productLikeRepository.save(match { it.productId == productId && it.userId == userId }) } returns AlreadyExists

            // when
            likeFacade.addLike(userId, productId)

            // then
            verify(exactly = 1) { productService.findProductById(productId) }
            verify(exactly = 1) { productLikeRepository.save(match { it.productId == productId && it.userId == userId }) }
            verify(exactly = 0) { productService.increaseProductLikeCount(productId) }
        }

        @Test
        @DisplayName("같은 좋아요를 여러 번 추가 시 첫 번째만 count가 증가한다")
        fun `increases count only once when multiple duplicate requests are made`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productService.findProductById(productId) } returns mockk()
            every { productLikeRepository.save(match { it.productId == productId && it.userId == userId }) } returnsMany listOf(
                Created,
                AlreadyExists,
                AlreadyExists,
                AlreadyExists,
            )

            every { productService.increaseProductLikeCount(productId) } just runs

            // when
            repeat(4) {
                likeFacade.addLike(userId, productId)
            }

            // then
            verify(exactly = 4) { productService.findProductById(productId) }
            verify(exactly = 4) { productLikeRepository.save(match { it.productId == productId && it.userId == userId }) }
            verify(exactly = 1) { productService.increaseProductLikeCount(productId) }
        }

        @Test
        @DisplayName("없는 좋아요 제거 시 count가 감소하지 않는다")
        fun `does not decrease count when like does not exist`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productService.findProductById(productId) } returns mockk()
            every { productLikeRepository.deleteByUserIdAndProductId(userId, productId) } returns NotExist

            // when
            likeFacade.removeLike(userId, productId)

            // then
            verify(exactly = 1) { productService.findProductById(productId) }
            verify(exactly = 1) { productLikeRepository.deleteByUserIdAndProductId(userId, productId) }
            verify(exactly = 0) { productService.decreaseProductLikeCount(productId) }
        }

        @Test
        @DisplayName("같은 좋아요를 여러 번 제거 시 첫 번째만 count가 감소한다")
        fun `decreases count only once when multiple duplicate remove requests are made`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productService.findProductById(productId) } returns mockk()
            every { productLikeRepository.deleteByUserIdAndProductId(userId, productId) } returnsMany listOf(
                Deleted,
                NotExist,
                NotExist,
                NotExist,
            )
            every { productService.decreaseProductLikeCount(productId) } just runs

            // when
            repeat(4) {
                likeFacade.removeLike(userId, productId)
            }

            // then
            verify(exactly = 4) { productService.findProductById(productId) }
            verify(exactly = 4) { productLikeRepository.deleteByUserIdAndProductId(userId, productId) }
            verify(exactly = 1) { productService.decreaseProductLikeCount(productId) }
        }
    }

    @Nested
    @DisplayName("상품 존재 여부 검증 테스트")
    inner class ProductValidation {
        @Test
        @DisplayName("좋아요 추가 시 상품 존재 여부를 확인한다")
        fun `verify product exists when adding like`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productService.findProductById(productId) } returns mockk()
            every { productLikeRepository.save(match { it.productId == productId && it.userId == userId }) } returns Created
            every { productService.increaseProductLikeCount(productId) } just runs

            // when
            likeFacade.addLike(userId, productId)

            // then
            verify(exactly = 1) { productService.findProductById(productId) }
        }

        @Test
        @DisplayName("좋아요 제거 시 상품 존재 여부를 확인한다")
        fun `verify product exists when removing like`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productService.findProductById(productId) } returns mockk()
            every { productLikeRepository.deleteByUserIdAndProductId(userId, productId) } returns Deleted
            every { productService.decreaseProductLikeCount(productId) } just runs

            // when
            likeFacade.removeLike(userId, productId)

            // then
            verify(exactly = 1) { productService.findProductById(productId) }
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 추가 시 예외가 전파된다")
        fun `propagate exception when adding like to non existing product`() {
            // given
            val userId = 1L
            val productId = 999L
            val exception = com.loopers.support.error.CoreException(
                errorType = com.loopers.support.error.ErrorType.NOT_FOUND,
                customMessage = "상품을 찾을 수 없습니다.",
            )

            every { productService.findProductById(productId) } throws exception

            // when & then
            assertThrows<com.loopers.support.error.CoreException> {
                likeFacade.addLike(userId, productId)
            }

            verify(exactly = 1) { productService.findProductById(productId) }
            verify(exactly = 0) { productLikeRepository.save(any()) }
            verify(exactly = 0) { productService.increaseProductLikeCount(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 상품의 좋아요 제거 시 예외가 전파된다")
        fun `propagate exception when removing like from non existing product`() {
            // given
            val userId = 1L
            val productId = 999L
            val exception = com.loopers.support.error.CoreException(
                errorType = com.loopers.support.error.ErrorType.NOT_FOUND,
                customMessage = "상품을 찾을 수 없습니다.",
            )

            every { productService.findProductById(productId) } throws exception

            // when & then
            assertThrows<com.loopers.support.error.CoreException> {
                likeFacade.removeLike(userId, productId)
            }

            verify(exactly = 1) { productService.findProductById(productId) }
            verify(exactly = 0) { productLikeRepository.deleteByUserIdAndProductId(any(), any()) }
            verify(exactly = 0) { productService.decreaseProductLikeCount(any()) }
        }
    }
}
