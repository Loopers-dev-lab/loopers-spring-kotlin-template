package com.loopers.application.cache

import com.loopers.domain.cache.ProductCacheService
import com.loopers.domain.event.product.StockDecreasedEvent
import com.loopers.infrastructure.event.EventHandledRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class CacheInvalidationFacadeTest {

    private lateinit var cacheInvalidationFacade: CacheInvalidationFacade
    private lateinit var productCacheService: ProductCacheService
    private lateinit var eventHandledRepository: EventHandledRepository

    @BeforeEach
    fun setUp() {
        productCacheService = mockk(relaxed = true)
        eventHandledRepository = mockk(relaxed = true)

        // save 메서드는 파라미터를 그대로 반환
        every { eventHandledRepository.save(any()) } answers { firstArg() }

        cacheInvalidationFacade = CacheInvalidationFacade(
            productCacheService,
            eventHandledRepository
        )
    }

    @DisplayName("StockDecreasedEvent 수신 시 상품 캐시를 무효화한다")
    @Test
    fun handleStockDecreasedEvent() {
        // given
        val event = StockDecreasedEvent(
            eventId = "event-123",
            aggregateId = 1L,
            productId = 1L,
            orderId = 100L,
            quantity = 5,
            remainingStock = 10
        )

        // when
        cacheInvalidationFacade.handleEvent(event)

        // then
        verify(exactly = 1) { productCacheService.invalidateProductCache(1L) }
        verify(exactly = 0) { productCacheService.invalidateProductListCache() }
        verify(exactly = 1) { eventHandledRepository.save(any()) }
    }

    @DisplayName("재고가 소진되면 상품 캐시와 목록 캐시를 모두 무효화한다")
    @Test
    fun handleStockDecreasedEventWhenStockIsZero() {
        // given
        val event = StockDecreasedEvent(
            eventId = "event-456",
            aggregateId = 2L,
            productId = 2L,
            orderId = 200L,
            quantity = 10,
            remainingStock = 0  // 재고 소진
        )

        // when
        cacheInvalidationFacade.handleEvent(event)

        // then
        verify(exactly = 1) { productCacheService.invalidateProductCache(2L) }
        verify(exactly = 1) { productCacheService.invalidateProductListCache() }
        verify(exactly = 1) { eventHandledRepository.save(any()) }
    }

    @DisplayName("중복 이벤트는 무시한다")
    @Test
    fun ignoreDuplicateEvent() {
        // given
        val event = StockDecreasedEvent(
            eventId = "event-789",
            aggregateId = 3L,
            productId = 3L,
            orderId = 300L,
            quantity = 5,
            remainingStock = 5
        )

        // save 시 중복 예외 발생 시뮬레이션
        every { eventHandledRepository.save(any()) } throws DataIntegrityViolationException("Duplicate key")

        // when
        cacheInvalidationFacade.handleEvent(event)

        // then
        verify(exactly = 0) { productCacheService.invalidateProductCache(any()) }
        verify(exactly = 0) { productCacheService.invalidateProductListCache() }
    }
}
