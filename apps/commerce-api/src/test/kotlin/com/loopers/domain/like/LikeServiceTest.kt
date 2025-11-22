package com.loopers.domain.like

import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class LikeServiceTest {
    @Autowired
    private lateinit var likeService: LikeService

    @MockkBean
    private lateinit var likeRepository: LikeRepository

    @Test
    fun `좋아요를 등록할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns false
        every { likeRepository.save(any()) } returns Like(userId = userId, productId = productId)

        // when
        likeService.addLike(userId, productId)

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
        likeService.addLike(userId, productId)

        // then
        verify(exactly = 0) { likeRepository.save(any()) }
    }

    @Test
    fun `좋아요를 취소할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { likeRepository.deleteByUserIdAndProductId(userId, productId) } just Runs

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
        every { likeRepository.deleteByUserIdAndProductId(userId, productId) } just Runs

        // when
        likeService.removeLike(userId, productId)

        // then (예외 발생 없이 deleteByUserIdAndProductId 호출)
        verify { likeRepository.deleteByUserIdAndProductId(userId, productId) }
    }
}
