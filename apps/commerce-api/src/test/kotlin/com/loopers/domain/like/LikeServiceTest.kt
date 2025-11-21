package com.loopers.domain.like

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LikeServiceTest {

    private val likeRepository: LikeRepository = mockk()
    private val likeService = LikeService(likeRepository)

    @DisplayName("좋아요가 정상적으로 추가된다.")
    @Test
    fun addLike_success() {
        // arrange
        val userId = 1L
        val productId = 100L
        val like = Like.of(userId, productId)

        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns false
        every { likeRepository.save(any()) } returns like

        // act
        likeService.addLike(userId, productId)

        // assert
        verify(exactly = 1) { likeRepository.existsByUserIdAndProductId(userId, productId) }
        verify(exactly = 1) { likeRepository.save(any()) }
    }

    @DisplayName("이미 존재하는 좋아요는 중복 처리되지 않는다.")
    @Test
    fun addLike_whenAlreadyExists_doesNotSave() {
        // arrange
        val userId = 1L
        val productId = 100L

        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns true

        // act
        likeService.addLike(userId, productId)

        // assert
        verify(exactly = 1) { likeRepository.existsByUserIdAndProductId(userId, productId) }
        verify(exactly = 0) { likeRepository.save(any()) }
    }

    @DisplayName("좋아요가 정상적으로 취소된다.")
    @Test
    fun removeLike_success() {
        // arrange
        val userId = 1L
        val productId = 100L
        val like = Like.of(userId, productId)

        every { likeRepository.findByUserIdAndProductId(userId, productId) } returns like
        every { likeRepository.delete(like) } just runs

        // act
        likeService.removeLike(userId, productId)

        // assert
        verify(exactly = 1) { likeRepository.findByUserIdAndProductId(userId, productId) }
        verify(exactly = 1) { likeRepository.delete(like) }
    }
}
