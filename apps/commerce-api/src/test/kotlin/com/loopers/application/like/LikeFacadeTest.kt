package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LikeFacadeTest {
    private val likeService: LikeService = mockk(relaxed = true)
    private val productRepository: ProductRepository = mockk()

    private val likeFacade = LikeFacade(
        likeService,
        productRepository,
    )

    @Test
    fun `존재하는 상품에 좋아요를 등록할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { productRepository.existsById(productId) } returns true

        // when
        likeFacade.addLike(userId, productId)

        // then
        verify { likeService.addLike(userId, productId) }
    }

    @Test
    fun `존재하지 않는 상품에 좋아요를 시도하면 예외가 발생한다`() {
        // given
        val userId = 1L
        val productId = 999L
        every { productRepository.existsById(productId) } returns false

        // when & then
        assertThatThrownBy {
            likeFacade.addLike(userId, productId)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }

    @Test
    fun `존재하는 상품에 좋아요를 취소할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { productRepository.existsById(productId) } returns true

        // when
        likeFacade.removeLike(userId, productId)

        // then
        verify { likeService.removeLike(userId, productId) }
    }

    @Test
    fun `존재하지 않는 상품에 좋아요 취소를 시도하면 예외가 발생한다`() {
        // given
        val userId = 1L
        val productId = 999L
        every { productRepository.existsById(productId) } returns false

        // when & then
        assertThatThrownBy {
            likeFacade.removeLike(userId, productId)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }
}
