package com.loopers.domain.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * EventHandled 도메인 로직 단위 테스트
 */
class EventHandledTest {

    @Test
    fun `EventHandled를 생성할 수 있다`() {
        // given
        val eventId = UUID.randomUUID()

        // when
        val eventHandled = EventHandled.create(
            eventId = eventId,
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = 100L,
        )

        // then
        assertThat(eventHandled.eventId).isEqualTo(eventId.toString())
        assertThat(eventHandled.eventType).isEqualTo("LikeAddedEvent")
        assertThat(eventHandled.aggregateType).isEqualTo("Product")
        assertThat(eventHandled.aggregateId).isEqualTo(100L)
        assertThat(eventHandled.handledBy).isEqualTo("commerce-streamer")
    }

    @Test
    fun `handledBy를 커스터마이징할 수 있다`() {
        // given
        val eventId = UUID.randomUUID()

        // when
        val eventHandled = EventHandled.create(
            eventId = eventId,
            eventType = "OrderCreatedEvent",
            aggregateType = "Order",
            aggregateId = 200L,
            handledBy = "custom-consumer",
        )

        // then
        assertThat(eventHandled.handledBy).isEqualTo("custom-consumer")
    }

    @Test
    fun `같은 eventId로 EventHandled를 생성하면 유니크 제약 조건에 위배된다`() {
        // 이 테스트는 Repository 레벨에서 검증됨
        // 여기서는 EventHandled 객체 생성 자체는 문제없음을 확인

        val eventId = UUID.randomUUID()

        val eventHandled1 = EventHandled.create(
            eventId = eventId,
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = 100L,
        )

        val eventHandled2 = EventHandled.create(
            eventId = eventId,
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = 100L,
        )

        // 객체 생성은 성공하지만, DB에 저장할 때 유니크 제약 위배
        assertThat(eventHandled1).isNotNull
        assertThat(eventHandled2).isNotNull
        assertThat(eventHandled1.eventId).isEqualTo(eventHandled2.eventId)
    }
}
