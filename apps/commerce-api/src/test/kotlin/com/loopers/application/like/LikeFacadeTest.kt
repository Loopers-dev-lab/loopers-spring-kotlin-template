package com.loopers.application.like

import com.loopers.domain.like.ProductLikeRepository
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

        productService = ProductService(
            productRepository = mockk(),
            productStatisticRepository = productStatisticRepository,
            brandRepository = mockk(),
        )

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

            every { productLikeRepository.upsert(match { it.productId == productId && it.userId == userId }) } returns 0

            // when
            likeFacade.addLike(userId, productId)

            // then
            verify(exactly = 1) { productLikeRepository.upsert(match { it.productId == productId && it.userId == userId }) }
            verify(exactly = 0) { productStatisticRepository.increaseLikeCountBy(productId) }
        }

        @Test
        @DisplayName("같은 좋아요를 여러 번 추가 시 첫 번째만 count가 증가한다")
        fun `increases count only once when multiple duplicate requests are made`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productLikeRepository.upsert(match { it.productId == productId && it.userId == userId }) } returnsMany listOf(
                1,
                0,
                0,
                0,
            )

            every { productStatisticRepository.increaseLikeCountBy(productId) } just runs

            // when
            repeat(4) {
                likeFacade.addLike(userId, productId)
            }

            // then
            verify(exactly = 4) { productLikeRepository.upsert(match { it.productId == productId && it.userId == userId }) }
            verify(exactly = 1) { productStatisticRepository.increaseLikeCountBy(productId) }
        }

        @Test
        @DisplayName("없는 좋아요 제거 시 count가 감소하지 않는다")
        fun `does not decrease count when like does not exist`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productLikeRepository.deleteByUserIdAndProductId(userId, productId) } returns 0L

            // when
            likeFacade.removeLike(userId, productId)

            // then
            verify(exactly = 1) { productLikeRepository.deleteByUserIdAndProductId(userId, productId) }
            verify(exactly = 0) { productStatisticRepository.decreaseLikeCountBy(productId) }
        }

        @Test
        @DisplayName("같은 좋아요를 여러 번 제거 시 첫 번째만 count가 감소한다")
        fun `decreases count only once when multiple duplicate remove requests are made`() {
            // given
            val userId = 1L
            val productId = 100L

            every { productLikeRepository.deleteByUserIdAndProductId(userId, productId) } returnsMany listOf(
                1L,
                0L,
                0L,
                0L,
            )
            every { productStatisticRepository.decreaseLikeCountBy(productId) } just runs

            // when
            repeat(4) {
                likeFacade.removeLike(userId, productId)
            }

            // then
            verify(exactly = 4) { productLikeRepository.deleteByUserIdAndProductId(userId, productId) }
            verify(exactly = 1) { productStatisticRepository.decreaseLikeCountBy(productId) }
        }
    }
}
