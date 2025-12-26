package com.loopers.application.facade

import com.loopers.domain.event.EventHandled
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.order.event.OrderItemDto
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingService
import com.loopers.domain.ranking.RankingWeights
import com.loopers.infrastructure.event.EventHandledRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@DisplayName("RankingEventFacade 테스트")
class RankingEventFacadeTest {

    private lateinit var rankingService: RankingService
    private lateinit var rankingScoreCalculator: RankingScoreCalculator
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var rankingEventFacade: RankingEventFacade

    @BeforeEach
    fun setUp() {
        rankingService = mockk(relaxed = true)

        // RankingWeights 생성 (기본값 사용)
        val rankingWeights = RankingWeights(
            view = 0.1,
            like = 0.2,
            unlike = -0.2,
            order = 0.6
        )
        rankingScoreCalculator = RankingScoreCalculator(rankingWeights)

        eventHandledRepository = mockk(relaxed = true)

        // findAllById는 빈 리스트 반환 (중복 없음)
        every { eventHandledRepository.findAllById(any<List<String>>()) } returns emptyList()

        // saveAll은 그대로 반환
        every { eventHandledRepository.saveAll(any<List<EventHandled>>()) } answers { firstArg() }

        rankingEventFacade = RankingEventFacade(
            rankingService,
            rankingScoreCalculator,
            eventHandledRepository
        )
    }

    @DisplayName("OrderCreatedEvent를 처리하면 주문 아이템별로 랭킹 점수가 증가한다")
    @Test
    fun handleOrderCreatedEvent() {
        // given
        val event = OrderCreatedEvent(
            eventId = "order-1",
            eventType = "ORDER_CREATED",
            aggregateId = 1L,
            occurredAt = Instant.parse("2025-12-25T10:00:00Z"),
            orderId = 1L,
            memberId = "user1",
            orderAmount = 25000L,
            couponId = null,
            orderItems = listOf(
                OrderItemDto(productId = 101L, quantity = 2, price = 10000L),
                OrderItemDto(productId = 102L, quantity = 1, price = 5000L)
            )
        )

        // when
        rankingEventFacade.handleBatchEvents(listOf(event))

        // then
        val expectedDate = LocalDate.of(2025, 12, 25)
        verify(exactly = 1) {
            rankingService.incrementScore(expectedDate, 101L, any())
        }
        verify(exactly = 1) {
            rankingService.incrementScore(expectedDate, 102L, any())
        }
    }

    @DisplayName("중복 이벤트는 처리되지 않는다")
    @Test
    fun skipDuplicateEvents() {
        // given
        val event = OrderCreatedEvent(
            eventId = "order-1",
            eventType = "ORDER_CREATED",
            aggregateId = 1L,
            occurredAt = Instant.parse("2025-12-25T10:00:00Z"),
            orderId = 1L,
            memberId = "user1",
            orderAmount = 10000L,
            couponId = null,
            orderItems = listOf(OrderItemDto(productId = 101L, quantity = 1, price = 10000L))
        )

        val alreadyHandled = EventHandled(
            eventId = "order-1",
            eventType = "ORDER_CREATED",
            occurredAt = event.occurredAt,
            handledAt = Instant.now()
        )

        // 이미 처리된 이벤트로 설정
        every { eventHandledRepository.findAllById(listOf("order-1")) } returns listOf(alreadyHandled)

        // when
        rankingEventFacade.handleBatchEvents(listOf(event))

        // then: 중복이므로 incrementScore 호출 안됨
        verify(exactly = 0) {
            rankingService.incrementScore(any(), any(), any())
        }

        // saveAll도 호출 안됨 (처리할 이벤트가 없으므로)
        verify(exactly = 0) {
            eventHandledRepository.saveAll(any<List<EventHandled>>())
        }
    }
}
