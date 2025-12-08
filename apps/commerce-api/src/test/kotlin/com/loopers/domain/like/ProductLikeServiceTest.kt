package com.loopers.domain.like

import com.loopers.domain.product.Product
import com.loopers.domain.user.User
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProductLikeService 테스트")
class ProductLikeServiceTest {

    private val productLikeRepository: ProductLikeRepository = mockk()
    private val productLikeService = ProductLikeService(productLikeRepository)

    @Nested
    @DisplayName("like 메서드는")
    inner class Like {

        @Test
        fun `좋아요가 이미 존재하면 save를 호출하지 않는다`() {
            // given
            val productId = 1L
            val userId = 100L
            val product = mockk<Product>()
            val user = mockk<User>()

            every { product.id } returns productId
            every { user.id } returns userId
            every { productLikeRepository.existsBy(productId, userId) } returns true

            // when
            productLikeService.like(product, user)

            // then
            verify(exactly = 1) { productLikeRepository.existsBy(productId, userId) }
            verify(exactly = 0) { productLikeRepository.save(any()) }
        }

        @Test
        fun `좋아요가 존재하지 않고 카운트가 존재하면 save를 호출한다`() {
            // given
            val productId = 1L
            val userId = 100L
            val product = mockk<Product>()
            val user = mockk<User>()

            every { product.id } returns productId
            every { user.id } returns userId
            every { productLikeRepository.existsBy(productId, userId) } returns false
            every { productLikeRepository.save(any()) } returns mockk()

            // when
            productLikeService.like(product, user)

            // then
            verify(exactly = 1) { productLikeRepository.existsBy(productId, userId) }
            verify(exactly = 0) { productLikeRepository.saveCount(any()) }
            verify(exactly = 1) { productLikeRepository.save(any()) }
        }

        @Test
        fun `좋아요가 존재하지 않고 카운트가 없으면 save를 호출한다`() {
            // given
            val productId = 1L
            val userId = 100L
            val product = mockk<Product>()
            val user = mockk<User>()

            every { product.id } returns productId
            every { user.id } returns userId
            every { productLikeRepository.existsBy(productId, userId) } returns false
            every { productLikeRepository.save(any()) } returns mockk()

            // when
            productLikeService.like(product, user)

            // then
            verify(exactly = 1) { productLikeRepository.existsBy(productId, userId) }
            verify(exactly = 1) { productLikeRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("unlike 메서드는")
    inner class Unlike {

        @Test
        fun `좋아요가 존재하면 deleteBy를 호출한다`() {
            // given
            val productId = 1L
            val userId = 100L
            val product = mockk<Product>()
            val user = mockk<User>()
            val existingProductLike = ProductLike.create(productId, userId)

            every { product.id } returns productId
            every { user.id } returns userId
            every { productLikeRepository.findBy(productId, userId) } returns existingProductLike
            justRun { productLikeRepository.deleteBy(productId, userId) }

            // when
            productLikeService.unlike(product, user)

            // then
            verify(exactly = 1) { productLikeRepository.findBy(productId, userId) }
            verify(exactly = 1) { productLikeRepository.deleteBy(productId, userId) }
        }

        @Test
        fun `좋아요가 존재하지 않으면 deleteBy를 호출하지 않는다`() {
            // given
            val productId = 1L
            val userId = 100L
            val product = mockk<Product>()
            val user = mockk<User>()

            every { product.id } returns productId
            every { user.id } returns userId
            every { productLikeRepository.findBy(productId, userId) } returns null

            // when
            productLikeService.unlike(product, user)

            // then
            verify(exactly = 1) { productLikeRepository.findBy(productId, userId) }
            verify(exactly = 0) { productLikeRepository.deleteBy(any(), any()) }
        }
    }
}
