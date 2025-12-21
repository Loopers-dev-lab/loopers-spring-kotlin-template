package com.loopers.application.metrics

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.metrics.ProductMetricsService
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.order.event.OrderItemDto
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.infrastructure.event.EventHandledRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * MetricsEventFacade 테스트
 *
 * 테스트 범위:
 * - 이벤트 수신 및 ProductMetrics 업데이트
 * - 멱등성 보장 (중복 이벤트 무시)
 * - 이벤트 타입별 라우팅
 * - EventHandled 테이블 기록
 */
class MetricsEventFacadeTest {

    private lateinit var productMetricsService: ProductMetricsService
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var metricsEventFacade: MetricsEventFacade

    @BeforeEach
    fun setUp() {
        productMetricsService = mockk(relaxed = true)
        eventHandledRepository = mockk(relaxed = true)
        // save() 메서드가 EventHandled를 반환하도록 명시적으로 설정
        every { eventHandledRepository.save(any<EventHandled>()) } answers { firstArg() }
        metricsEventFacade = MetricsEventFacade(
            productMetricsService,
            eventHandledRepository
        )
    }

    @DisplayName("ProductLikedEvent 처리 시 좋아요 수가 증가한다")
    @Test
    fun handleProductLikedEvent() {
        // given
        val event = ProductLikedEvent(
            eventId = "event-1",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "user1",
            occurredAt = Instant.now()
        )
        every { eventHandledRepository.existsByEventId(any()) } returns false

        // when
        metricsEventFacade.handleEvent(event)

        // then
        verify(exactly = 1) {
            productMetricsService.incrementLikes(100L, any())
        }
        verify(exactly = 1) {
            eventHandledRepository.save(match {
                it.eventId == "event-1" && it.eventType == "PRODUCT_LIKED"
            })
        }
    }

    @DisplayName("ProductUnlikedEvent 처리 시 좋아요 수가 감소한다")
    @Test
    fun handleProductUnlikedEvent() {
        // given
        val event = ProductUnlikedEvent(
            eventId = "event-2",
            aggregateId = 200L,
            productId = 200L,
            memberId = "user2",
            occurredAt = Instant.now()
        )
        every { eventHandledRepository.existsByEventId(any()) } returns false

        // when
        metricsEventFacade.handleEvent(event)

        // then
        verify(exactly = 1) {
            productMetricsService.decrementLikes(200L, any())
        }
    }

    @DisplayName("ProductViewedEvent 처리 시 조회 수가 증가한다")
    @Test
    fun handleProductViewedEvent() {
        // given
        val event = ProductViewedEvent(
            eventId = "event-3",
            aggregateId = 300L,
            productId = 300L,
            memberId = "user3",
            occurredAt = Instant.now()
        )
        every { eventHandledRepository.existsByEventId(any()) } returns false

        // when
        metricsEventFacade.handleEvent(event)

        // then
        verify(exactly = 1) {
            productMetricsService.incrementViews(300L, any())
        }
    }

    @DisplayName("OrderCreatedEvent 처리 시 주문 상품별 판매량이 증가한다")
    @Test
    fun handleOrderCreatedEvent() {
        // given
        val orderItems = listOf(
            OrderItemDto(productId = 100L, quantity = 2, price = 10000L),
            OrderItemDto(productId = 200L, quantity = 1, price = 20000L)
        )
        val event = OrderCreatedEvent(
            eventId = "event-4",
            aggregateId = 1000L,
            orderId = 1000L,
            memberId = "user4",
            orderAmount = 40000L,
            couponId = null,
            orderItems = orderItems,
            occurredAt = Instant.now()
        )
        every { eventHandledRepository.existsByEventId(any()) } returns false

        // when
        metricsEventFacade.handleEvent(event)

        // then: 각 상품별로 판매량 증가
        verify(exactly = 1) {
            productMetricsService.incrementSales(100L, any(), 2)
        }
        verify(exactly = 1) {
            productMetricsService.incrementSales(200L, any(), 1)
        }
    }

    @DisplayName("중복 이벤트는 무시된다 (멱등성)")
    @Test
    fun ignoreDuplicateEvent() {
        // given
        val event = ProductLikedEvent(
            eventId = "duplicate-event",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "user1",
            occurredAt = Instant.now()
        )
        every { eventHandledRepository.existsByEventId("duplicate-event") } returns true

        // when
        metricsEventFacade.handleEvent(event)

        // then: 처리되지 않음
        verify(exactly = 0) {
            productMetricsService.incrementLikes(any(), any())
        }
        verify(exactly = 0) {
            eventHandledRepository.save(any())
        }
    }

    @DisplayName("동일한 eventId로 여러 번 호출해도 한 번만 처리된다")
    @Test
    fun handleEventOnlyOnce() {
        // given
        val event = ProductLikedEvent(
            eventId = "event-5",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "user1",
            occurredAt = Instant.now()
        )

        // 첫 번째 호출: 처리되지 않음 (false)
        // 두 번째 호출: 이미 처리됨 (true)
        every { eventHandledRepository.existsByEventId("event-5") } returnsMany listOf(false, true)

        // when: 2번 호출
        metricsEventFacade.handleEvent(event)
        metricsEventFacade.handleEvent(event)

        // then: 1번만 처리됨
        verify(exactly = 1) {
            productMetricsService.incrementLikes(100L, any())
        }
        verify(exactly = 1) {
            eventHandledRepository.save(any())
        }
    }

    @DisplayName("EventHandled 저장 시 eventId와 eventType이 올바르게 기록된다")
    @Test
    fun saveEventHandledCorrectly() {
        // given
        val event = ProductLikedEvent(
            eventId = "event-6",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "user1",
            occurredAt = Instant.now()
        )
        every { eventHandledRepository.existsByEventId(any()) } returns false

        val savedSlot = slot<EventHandled>()
        every { eventHandledRepository.save(capture(savedSlot)) } returns mockk()

        // when
        metricsEventFacade.handleEvent(event)

        // then
        assertThat(savedSlot.captured.eventId).isEqualTo("event-6")
        assertThat(savedSlot.captured.eventType).isEqualTo("PRODUCT_LIKED")
        assertThat(savedSlot.captured.handledAt).isNotNull()
    }

    @DisplayName("여러 이벤트를 순차적으로 처리한다")
    @Test
    fun handleMultipleEventsSequentially() {
        // given
        val event1 = ProductLikedEvent(
            eventId = "event-7",
            aggregateId = 100L,
            likeId = 1L,
            productId = 100L,
            memberId = "user1",
            occurredAt = Instant.now()
        )
        val event2 = ProductViewedEvent(
            eventId = "event-8",
            aggregateId = 200L,
            productId = 200L,
            memberId = "user2",
            occurredAt = Instant.now()
        )
        every { eventHandledRepository.existsByEventId(any()) } returns false

        // when
        metricsEventFacade.handleEvent(event1)
        metricsEventFacade.handleEvent(event2)

        // then
        verify(exactly = 1) { productMetricsService.incrementLikes(100L, any()) }
        verify(exactly = 1) { productMetricsService.incrementViews(200L, any()) }
        verify(exactly = 2) { eventHandledRepository.save(any()) }
    }
}
