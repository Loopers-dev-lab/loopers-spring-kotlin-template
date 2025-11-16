package com.loopers.domain.like

import com.loopers.domain.product.Product
import com.loopers.domain.user.User
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("ProductLikeService 테스트")
class ProductLikeServiceTest {

    @Mock
    private lateinit var productLikeRepository: ProductLikeRepository

    @InjectMocks
    private lateinit var productLikeService: ProductLikeService

    @Nested
    @DisplayName("like 메서드는")
    inner class Like {

        @Mock
        private lateinit var product: Product

        @Mock
        private lateinit var user: User

        @Test
        fun `좋아요가 이미 존재하면 save를 호출하지 않는다`() {
            // given
            val productId = 1L
            val userId = 100L
            val existingProductLike = ProductLike.create(productId, userId)

            whenever(product.id).thenReturn(productId)
            whenever(user.id).thenReturn(userId)
            whenever(productLikeRepository.findBy(productId, userId)).thenReturn(existingProductLike)

            // when
            productLikeService.like(product, user)

            // then
            verify(productLikeRepository).findBy(productId, userId)
            verify(productLikeRepository, never()).save(any())
        }

        @Test
        fun `좋아요가 존재하지 않으면 save를 호출한다`() {
            // given
            val productId = 1L
            val userId = 100L

            whenever(product.id).thenReturn(productId)
            whenever(user.id).thenReturn(userId)
            whenever(productLikeRepository.findBy(productId, userId)).thenReturn(null)

            // when
            productLikeService.like(product, user)

            // then
            verify(productLikeRepository).findBy(productId, userId)
            verify(productLikeRepository).save(any())
        }
    }

    @Nested
    @DisplayName("unlike 메서드는")
    inner class Unlike {

        @Mock
        private lateinit var product: Product

        @Mock
        private lateinit var user: User

        @Test
        fun `좋아요가 존재하면 deleteBy를 호출한다`() {
            // given
            val productId = 1L
            val userId = 100L
            val existingProductLike = ProductLike.create(productId, userId)

            whenever(product.id).thenReturn(productId)
            whenever(user.id).thenReturn(userId)
            whenever(productLikeRepository.findBy(productId, userId)).thenReturn(existingProductLike)

            // when
            productLikeService.unlike(product, user)

            // then
            verify(productLikeRepository).findBy(productId, userId)
            verify(productLikeRepository).deleteBy(productId, userId)
        }

        @Test
        fun `좋아요가 존재하지 않으면 deleteBy를 호출하지 않는다`() {
            // given
            val productId = 1L
            val userId = 100L

            whenever(product.id).thenReturn(productId)
            whenever(user.id).thenReturn(userId)
            whenever(productLikeRepository.findBy(productId, userId)).thenReturn(null)

            // when
            productLikeService.unlike(product, user)

            // then
            verify(productLikeRepository).findBy(productId, userId)
            verify(productLikeRepository, never()).deleteBy(any(), any())
        }
    }
}
