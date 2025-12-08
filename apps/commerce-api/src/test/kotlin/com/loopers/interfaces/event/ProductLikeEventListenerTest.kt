package com.loopers.interfaces.event

import com.loopers.application.like.event.ProductLikeEvent
import com.loopers.application.product.ProductCache
import com.loopers.domain.like.ProductLikeCount
import com.loopers.domain.like.ProductLikeRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("ProductLikeEventListener 단위 테스트")
class ProductLikeEventListenerTest {

    private val productLikeRepository: ProductLikeRepository = mockk()
    private val productCache: ProductCache = mockk()
    private val transactionTemplate: TransactionTemplate = mockk()

    private val productLikeEventListener = ProductLikeEventListener(
        productLikeRepository = productLikeRepository,
        productCache = productCache,
        transactionTemplate = transactionTemplate,
    )

    @Nested
    @DisplayName("좋아요 추가 이벤트 처리 - 집계 증가")
    inner class HandleProductLiked {

        @Test
        @DisplayName("좋아요 집계를 증가시키고 캐시를 무효화한다")
        fun `should increase like count and evict cache`() {
            // given
            val event = ProductLikeEvent.ProductLiked(
                productId = 1L,
                userId = "user123",
            )

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            every { productLikeRepository.increaseCount(1L) } returns 1

            justRun { productCache.evictProductList() }
            justRun { productCache.evictLikedProductList("user123") }
            justRun { productCache.evictProductDetail(1L) }

            // when
            productLikeEventListener.handleProductLiked(event)

            // then
            verify(exactly = 1) { productLikeRepository.increaseCount(1L) }
            verify(exactly = 0) { productLikeRepository.saveCount(any()) }
            verify(exactly = 1) { productCache.evictProductList() }
            verify(exactly = 1) { productCache.evictLikedProductList("user123") }
            verify(exactly = 1) { productCache.evictProductDetail(1L) }
        }

        @Test
        @DisplayName("집계 레코드가 없으면 새로 생성한다")
        fun `should create count record when it does not exist`() {
            // given
            val event = ProductLikeEvent.ProductLiked(
                productId = 1L,
                userId = "user123",
            )

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            every { productLikeRepository.increaseCount(1L) } returns 0
            justRun { productLikeRepository.saveCount(any()) }

            justRun { productCache.evictProductList() }
            justRun { productCache.evictLikedProductList("user123") }
            justRun { productCache.evictProductDetail(1L) }

            // when
            productLikeEventListener.handleProductLiked(event)

            // then
            verify(exactly = 1) { productLikeRepository.increaseCount(1L) }
            verify(exactly = 1) {
                productLikeRepository.saveCount(
                    match<ProductLikeCount> {
                        it.productId == 1L && it.likeCount == 1L
                    },
                )
            }
        }

        @Test
        @DisplayName("집계 증가 실패 시에도 캐시 무효화는 시도한다")
        fun `should try to evict cache even when count increase fails`() {
            // given
            val event = ProductLikeEvent.ProductLiked(
                productId = 1L,
                userId = "user123",
            )

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            every { productLikeRepository.increaseCount(1L) } throws RuntimeException("DB 오류")

            justRun { productCache.evictProductList() }
            justRun { productCache.evictLikedProductList("user123") }
            justRun { productCache.evictProductDetail(1L) }

            // when
            productLikeEventListener.handleProductLiked(event)

            // then
            verify(exactly = 1) { productLikeRepository.increaseCount(1L) }
            verify(exactly = 1) { productCache.evictProductList() }
            verify(exactly = 1) { productCache.evictLikedProductList("user123") }
            verify(exactly = 1) { productCache.evictProductDetail(1L) }
        }

        @Test
        @DisplayName("캐시 무효화 실패 시에도 예외가 전파되지 않는다")
        fun `should not propagate exception when cache eviction fails`() {
            // given
            val event = ProductLikeEvent.ProductLiked(
                productId = 1L,
                userId = "user123",
            )

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            every { productLikeRepository.increaseCount(1L) } returns 1

            every { productCache.evictProductList() } throws RuntimeException("캐시 오류")
            justRun { productCache.evictLikedProductList("user123") }
            justRun { productCache.evictProductDetail(1L) }

            // when
            productLikeEventListener.handleProductLiked(event)

            // then
            verify(exactly = 1) { productLikeRepository.increaseCount(1L) }
            verify(exactly = 1) { productCache.evictProductList() }
        }
    }

    @Nested
    @DisplayName("좋아요 취소 이벤트 처리 - 집계 감소")
    inner class HandleProductUnliked {

        @Test
        @DisplayName("좋아요 집계를 감소시키고 캐시를 무효화한다")
        fun `should decrease like count and evict cache`() {
            // given
            val event = ProductLikeEvent.ProductUnliked(
                productId = 1L,
                userId = "user123",
            )

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            every { productLikeRepository.decreaseCount(1L) } returns 1

            justRun { productCache.evictProductList() }
            justRun { productCache.evictLikedProductList("user123") }
            justRun { productCache.evictProductDetail(1L) }

            // when
            productLikeEventListener.handleProductUnliked(event)

            // then
            verify(exactly = 1) { productLikeRepository.decreaseCount(1L) }
            verify(exactly = 1) { productCache.evictProductList() }
            verify(exactly = 1) { productCache.evictLikedProductList("user123") }
            verify(exactly = 1) { productCache.evictProductDetail(1L) }
        }

        @Test
        @DisplayName("집계 감소 실패 시 예외가 전파되지 않는다")
        fun `should not propagate exception when count decrease fails`() {
            // given
            val event = ProductLikeEvent.ProductUnliked(
                productId = 1L,
                userId = "user123",
            )

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            every { productLikeRepository.decreaseCount(1L) } throws RuntimeException("DB 오류")

            // when
            productLikeEventListener.handleProductUnliked(event)

            // then
            verify(exactly = 1) { productLikeRepository.decreaseCount(1L) }
            verify(exactly = 0) { productCache.evictProductList() }
        }

        @Test
        @DisplayName("캐시 무효화 실패 시에도 예외가 전파되지 않는다")
        fun `should not propagate exception when cache eviction fails`() {
            // given
            val event = ProductLikeEvent.ProductUnliked(
                productId = 1L,
                userId = "user123",
            )

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            every { productLikeRepository.decreaseCount(1L) } returns 1

            every { productCache.evictProductList() } throws RuntimeException("캐시 오류")

            // when
            productLikeEventListener.handleProductUnliked(event)

            // then
            verify(exactly = 1) { productLikeRepository.decreaseCount(1L) }
            verify(exactly = 1) { productCache.evictProductList() }
        }
    }
}
