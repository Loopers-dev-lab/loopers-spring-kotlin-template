package com.loopers.domain.event.handler

import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.event.LikeRemovedEvent
import com.loopers.domain.event.UserActionEvent
import com.loopers.domain.outbox.OutboxEventPublisher
import com.loopers.domain.product.ProductCacheRepository
import com.loopers.domain.product.ProductLikeCountService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class LikeEventHandlerTest {
    private val productLikeCountService = mockk<ProductLikeCountService>()
    private val productCacheRepository = mockk<ProductCacheRepository>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val outboxEventPublisher = mockk<OutboxEventPublisher>(relaxed = true)

    private val likeEventHandler = LikeEventHandler(
        productLikeCountService,
        productCacheRepository,
        eventPublisher,
        outboxEventPublisher,
    )

    @DisplayName("좋아요 추가 이벤트를 처리할 때,")
    @Nested
    inner class HandleLikeAdded {
        @DisplayName("좋아요 수를 증가시키고 캐시를 무효화한다")
        @Test
        fun handleLikeAdded_incrementsLikeCountAndInvalidatesCache() {
            // given
            val event = LikeAddedEvent(
                userId = 1L,
                productId = 100L,
                createdAt = LocalDateTime.now(),
            )

            every { productLikeCountService.increment(100L) } returns 5L
            every { productCacheRepository.buildProductDetailCacheKey(100L) } returns "product:detail:100"
            every { productCacheRepository.getProductListCachePattern() } returns "product:list:*"

            // when
            likeEventHandler.handleLikeAdded(event)

            // then
            verify(exactly = 1) { productLikeCountService.increment(100L) }
            verify(exactly = 1) { productCacheRepository.buildProductDetailCacheKey(100L) }
            verify(exactly = 1) { productCacheRepository.delete("product:detail:100") }
            verify(exactly = 1) { productCacheRepository.getProductListCachePattern() }
            verify(exactly = 1) { productCacheRepository.deleteByPattern("product:list:*") }
            verify(exactly = 1) {
                outboxEventPublisher.publish(
                    eventType = "LikeAddedEvent",
                    topic = "catalog-events",
                    partitionKey = "100",
                    payload = event,
                    aggregateType = "Product",
                    aggregateId = 100L,
                )
            }
            verify(exactly = 1) { eventPublisher.publishEvent(ofType<UserActionEvent>()) }
        }

        @DisplayName("집계 처리 실패 시에도 예외가 전파되지 않는다")
        @Test
        fun handleLikeAdded_whenIncrementFails_thenDoesNotThrow() {
            // given
            val event = LikeAddedEvent(
                userId = 1L,
                productId = 100L,
                createdAt = LocalDateTime.now(),
            )

            every { productLikeCountService.increment(100L) } throws RuntimeException("Redis 오류")

            // when & then (예외가 발생하지 않아야 함)
            likeEventHandler.handleLikeAdded(event)

            verify(exactly = 1) { productLikeCountService.increment(100L) }
        }

        @DisplayName("캐시 무효화 실패 시에도 예외가 전파되지 않는다")
        @Test
        fun handleLikeAdded_whenCacheEvictionFails_thenDoesNotThrow() {
            // given
            val event = LikeAddedEvent(
                userId = 1L,
                productId = 100L,
                createdAt = LocalDateTime.now(),
            )

            every { productLikeCountService.increment(100L) } returns 5L
            every { productCacheRepository.buildProductDetailCacheKey(100L) } throws RuntimeException("캐시 오류")

            // when & then (예외가 발생하지 않아야 함)
            likeEventHandler.handleLikeAdded(event)

            verify(exactly = 1) { productLikeCountService.increment(100L) }
        }

        @DisplayName("Outbox 발행 실패 시에도 예외가 전파되지 않는다")
        @Test
        fun handleLikeAdded_whenOutboxPublishFails_thenDoesNotThrow() {
            // given
            val event = LikeAddedEvent(
                userId = 1L,
                productId = 100L,
                createdAt = LocalDateTime.now(),
            )

            every { productLikeCountService.increment(100L) } returns 5L
            every { productCacheRepository.buildProductDetailCacheKey(100L) } returns "product:detail:100"
            every { productCacheRepository.getProductListCachePattern() } returns "product:list:*"
            every {
                outboxEventPublisher.publish(
                    eventType = "LikeAddedEvent",
                    topic = "catalog-events",
                    partitionKey = "100",
                    payload = event,
                    aggregateType = "Product",
                    aggregateId = 100L,
                )
            } throws RuntimeException("Outbox 저장 오류")

            // when & then (예외가 발생하지 않아야 함)
            likeEventHandler.handleLikeAdded(event)

            verify(exactly = 1) {
                outboxEventPublisher.publish(
                    eventType = "LikeAddedEvent",
                    topic = "catalog-events",
                    partitionKey = "100",
                    payload = event,
                    aggregateType = "Product",
                    aggregateId = 100L,
                )
            }
        }
    }

    @DisplayName("좋아요 제거 이벤트를 처리할 때,")
    @Nested
    inner class HandleLikeRemoved {
        @DisplayName("좋아요 수를 감소시키고 캐시를 무효화한다")
        @Test
        fun handleLikeRemoved_decrementsLikeCountAndInvalidatesCache() {
            // given
            val event = LikeRemovedEvent(
                userId = 1L,
                productId = 100L,
                createdAt = LocalDateTime.now(),
            )

            every { productLikeCountService.decrement(100L) } returns 4L
            every { productCacheRepository.buildProductDetailCacheKey(100L) } returns "product:detail:100"
            every { productCacheRepository.getProductListCachePattern() } returns "product:list:*"

            // when
            likeEventHandler.handleLikeRemoved(event)

            // then
            verify(exactly = 1) { productLikeCountService.decrement(100L) }
            verify(exactly = 1) { productCacheRepository.buildProductDetailCacheKey(100L) }
            verify(exactly = 1) { productCacheRepository.delete("product:detail:100") }
            verify(exactly = 1) { productCacheRepository.getProductListCachePattern() }
            verify(exactly = 1) { productCacheRepository.deleteByPattern("product:list:*") }
            verify(exactly = 1) {
                outboxEventPublisher.publish(
                    eventType = "LikeRemovedEvent",
                    topic = "catalog-events",
                    partitionKey = "100",
                    payload = event,
                    aggregateType = "Product",
                    aggregateId = 100L,
                )
            }
            verify(exactly = 1) { eventPublisher.publishEvent(ofType<UserActionEvent>()) }
        }

        @DisplayName("집계 처리 실패 시에도 예외가 전파되지 않는다")
        @Test
        fun handleLikeRemoved_whenDecrementFails_thenDoesNotThrow() {
            // given
            val event = LikeRemovedEvent(
                userId = 1L,
                productId = 100L,
                createdAt = LocalDateTime.now(),
            )

            every { productLikeCountService.decrement(100L) } throws RuntimeException("Redis 오류")

            // when & then (예외가 발생하지 않아야 함)
            likeEventHandler.handleLikeRemoved(event)

            verify(exactly = 1) { productLikeCountService.decrement(100L) }
        }
    }
}
