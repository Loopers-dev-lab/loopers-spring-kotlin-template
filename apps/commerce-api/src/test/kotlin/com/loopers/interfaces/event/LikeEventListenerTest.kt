package com.loopers.interfaces.event

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.product.ProductService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LikeEventListenerTest {
    private lateinit var productService: ProductService
    private lateinit var likeEventListener: LikeEventListener

    @BeforeEach
    fun setUp() {
        productService = mockk()
        likeEventListener = LikeEventListener(productService)
    }

    @Nested
    @DisplayName("onLikeCreated")
    inner class OnLikeCreated {
        @Test
        @DisplayName("productService.increaseProductLikeCount(productId)를 호출한다")
        fun `calls productService increaseProductLikeCount with productId`() {
            // given
            val event = LikeCreatedEventV1(userId = 1L, productId = 100L)
            every { productService.increaseProductLikeCount(100L) } just runs

            // when
            likeEventListener.onLikeCreated(event)

            // then
            verify(exactly = 1) { productService.increaseProductLikeCount(100L) }
        }
    }

    @Nested
    @DisplayName("onLikeCanceled")
    inner class OnLikeCanceled {
        @Test
        @DisplayName("productService.decreaseProductLikeCount(productId)를 호출한다")
        fun `calls productService decreaseProductLikeCount with productId`() {
            // given
            val event = LikeCanceledEventV1(userId = 1L, productId = 100L)
            every { productService.decreaseProductLikeCount(100L) } just runs

            // when
            likeEventListener.onLikeCanceled(event)

            // then
            verify(exactly = 1) { productService.decreaseProductLikeCount(100L) }
        }
    }
}
