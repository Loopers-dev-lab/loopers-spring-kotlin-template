package com.loopers.domain.like

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LikeServiceTest {
    private val likeRepository: LikeRepository = mockk(relaxed = true)
    private lateinit var likeService: LikeService

    @BeforeEach
    fun setUp() {
        // 단위 테스트에서는 self-injection 동작을 테스트하지 않으므로
        // self를 mockk로 대체
        likeService = LikeService(likeRepository, mockk(relaxed = true))
    }

    @Test
    fun `좋아요를 등록할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns false

        // when
        // addLikeInternal을 직접 호출하여 테스트
        likeService.addLikeInternal(userId, productId)

        // then
        verify { likeRepository.save(any()) }
    }

    @Test
    fun `이미 좋아요한 상품에 다시 좋아요를 시도하면 멱등하게 동작한다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns true

        // when
        // addLikeInternal을 직접 호출하여 테스트
        likeService.addLikeInternal(userId, productId)

        // then
        verify(exactly = 0) { likeRepository.save(any()) }
    }

    @Test
    fun `좋아요를 취소할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L

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

        // when
        likeService.removeLike(userId, productId)

        // then (예외 발생 없이 deleteByUserIdAndProductId 호출)
        verify { likeRepository.deleteByUserIdAndProductId(userId, productId) }
    }
}
