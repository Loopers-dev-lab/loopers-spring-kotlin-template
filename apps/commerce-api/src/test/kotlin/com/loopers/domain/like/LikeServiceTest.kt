package com.loopers.domain.like

import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LikeServiceTest {
    private val likeRepository: LikeRepository = mockk(relaxed = true)
    private val productRepository: ProductRepository = mockk()
    private val likeService = LikeService(likeRepository, productRepository)

    @Test
    fun `존재하는 상품에 좋아요를 등록할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { productRepository.existsById(productId) } returns true
        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns false

        // when
        likeService.addLike(userId, productId)

        // then
        verify { likeRepository.save(any()) }
    }

    @Test
    fun `존재하지 않는 상품에 좋아요를 시도하면 예외가 발생한다`() {
        // given
        val userId = 1L
        val productId = 999L
        every { productRepository.existsById(productId) } returns false

        // when & then
        assertThatThrownBy {
            likeService.addLike(userId, productId)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }

    @Test
    fun `이미 좋아요한 상품에 다시 좋아요를 시도하면 멱등하게 동작한다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { productRepository.existsById(productId) } returns true
        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns true

        // when
        likeService.addLike(userId, productId)

        // then
        verify(exactly = 0) { likeRepository.save(any()) }
    }

    @Test
    fun `좋아요를 취소할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { productRepository.existsById(productId) } returns true

        // when
        likeService.removeLike(userId, productId)

        // then
        verify { likeRepository.deleteByUserIdAndProductId(userId, productId) }
    }

    @Test
    fun `좋아요하지 않은 상품에 대해 취소를 시도하면 멱등하게 동작한다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { productRepository.existsById(productId) } returns true

        // when
        likeService.removeLike(userId, productId)

        // then (예외 발생 없이 deleteByUserIdAndProductId 호출)
        verify { likeRepository.deleteByUserIdAndProductId(userId, productId) }
    }

    @Test
    fun `존재하지 않는 상품에 좋아요 취소를 시도하면 예외가 발생한다`() {
        // given
        val userId = 1L
        val productId = 999L
        every { productRepository.existsById(productId) } returns false

        // when & then
        assertThatThrownBy {
            likeService.removeLike(userId, productId)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }
}
