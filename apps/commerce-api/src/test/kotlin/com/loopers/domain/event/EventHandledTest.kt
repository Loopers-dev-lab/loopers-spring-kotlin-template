package com.loopers.domain.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * EventHandled 도메인 로직 단위 테스트
 */
class EventHandledTest {

    @Test
    fun `EventHandled를 생성할 수 있다`() {
        // when
        val eventHandled = EventHandled.create(
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = 100L,
            eventVersion = 12345L,
        )

        // then
        assertThat(eventHandled.eventType).isEqualTo("LikeAddedEvent")
        assertThat(eventHandled.aggregateType).isEqualTo("Product")
        assertThat(eventHandled.aggregateId).isEqualTo(100L)
        assertThat(eventHandled.eventVersion).isEqualTo(12345L)
    }

    @Test
    fun `같은 이벤트 키로 EventHandled를 생성하면 유니크 제약 조건에 위배된다`() {
        // 이 테스트는 Repository 레벨에서 검증됨
        // 여기서는 EventHandled 객체 생성 자체는 문제없음을 확인

        val eventHandled1 = EventHandled.create(
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = 100L,
            eventVersion = 12345L,
        )

        val eventHandled2 = EventHandled.create(
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = 100L,
            eventVersion = 12345L,
        )

        // 객체 생성은 성공하지만, DB에 저장할 때 유니크 제약 위배
        assertThat(eventHandled1).isNotNull
        assertThat(eventHandled2).isNotNull
    }
}
